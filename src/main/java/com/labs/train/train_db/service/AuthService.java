package com.labs.train.train_db.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.User;
import com.labs.train.train_db.exception.AccountLockedException;
import com.labs.train.train_db.exception.AuthFlowException;
import com.labs.train.train_db.exception.DuplicateUserException;
import com.labs.train.train_db.exception.InvalidCredentialsException;
import com.labs.train.train_db.model.AuthResponse;
import com.labs.train.train_db.model.ChangePasswordRequest;
import com.labs.train.train_db.model.CurrentUserResponse;
import com.labs.train.train_db.model.LoginRequest;
import com.labs.train.train_db.model.MfaEnrolmentResponse;
import com.labs.train.train_db.model.RegisterRequest;
import com.labs.train.train_db.repository.UserRepository;
import com.labs.train.train_db.security.MfaSecretCipher;
import com.labs.train.train_db.security.RailOpsAccountClient;
import com.labs.train.train_db.audit.AuditActions;
import com.labs.train.train_db.audit.SecurityAuditService;
import com.labs.train.train_db.security.Totp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

        static final String TOTP_ISSUER = "RailLens";

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final RefreshTokenService refreshTokenService;
        private final MfaSecretCipher mfaSecretCipher;
        private final RailOpsAccountClient railOpsAccounts;
        private final SecurityAuditService audit;

        @Value("${raillens.auth.max-failed-login-attempts:5}")
        private int maxFailedLoginAttempts;

        /** Phase 6: each lockout in a row doubles the lock time, up to this many minutes. */
        @Value("${raillens.auth.lockout-max-minutes:1440}")
        private long lockoutMaxMinutes;

        @Value("${raillens.auth.lockout-duration-minutes:15}")
        private long lockoutDurationMinutes;

        /** Replaceable in tests to land on a chosen TOTP step. */
        Clock clock = Clock.systemUTC();

        /** BCrypt of a random value: compared against for unknown users so they take as long as real ones. */
        private volatile String dummyPasswordHash;

        public AuthResponse register(RegisterRequest request) {

                if (userRepository.existsByUsername(request.username())) {
                        throw new DuplicateUserException("Username is already taken");
                }

                if (userRepository.existsByEmail(request.email())) {
                        throw new DuplicateUserException("Email is already registered");
                }

                User user = new User();
                user.setUsername(request.username());
                user.setEmail(request.email());
                user.setPasswordHash(passwordEncoder.encode(request.password()));

                userRepository.save(user);

                log.info("Registered new user: {}", user.getUsername());
                audit.record(user.getUsername(), AuditActions.REGISTERED, user.getUsername(), null);

                return issueSession(user, false);
        }

        /**
         * Users: password, as before. Admins: password, then a one-time code from their authenticator app
         * ({@code otp}). An admin who has not set up MFA gets an enrolment token instead of a session.
         */
        public AuthResponse login(LoginRequest request) {

                Optional<User> found = userRepository.findByUsernameOrEmail(request.usernameOrEmail(), request.usernameOrEmail());
                if (found.isEmpty()) {
                        // Same BCrypt cost as a real account, so response time doesn't reveal which usernames exist.
                        passwordEncoder.matches(request.password(), dummyPasswordHash());
                        log.warn("Failed login attempt for unknown username/email: {}", request.usernameOrEmail());
                        // What was typed isn't recorded: it may be someone's email or a mistyped password.
                        audit.record("unknown", AuditActions.LOGIN_FAILED, null, "unknown account");
                        throw new InvalidCredentialsException("Invalid username/email or password");
                }
                User user = found.get();

                if (user.getLockedUntil() != null) {
                        if (user.getLockedUntil().isAfter(LocalDateTime.now())) {
                                audit.record(user.getUsername(), AuditActions.LOGIN_BLOCKED_LOCKED, user.getUsername(), null);
                                throw new AccountLockedException(
                                                "Account temporarily locked due to too many failed login attempts. "
                                                                + "Try again later.");
                        }

                        user.setLockedUntil(null);
                        user.setFailedLoginAttempts(0);
                }

                // A null hash means the password lives in RailOps (linked, Phase 4D): same cost and answer as a wrong one.
                String hash = user.getPasswordHash() != null ? user.getPasswordHash() : dummyPasswordHash();
                if (!passwordEncoder.matches(request.password(), hash) || user.getPasswordHash() == null) {
                        registerFailedAttempt(user);
                        log.warn("Failed login attempt (wrong password) for user: {}", user.getUsername());
                        audit.record(user.getUsername(), AuditActions.LOGIN_FAILED, user.getUsername(), "wrong password");
                        throw new InvalidCredentialsException("Invalid username/email or password");
                }

                if (user.isAdmin()) {
                        return adminSecondFactor(user, request.otp());
                }

                clearFailedAttempts(user);
                audit.record(user.getUsername(), AuditActions.LOGIN_SUCCEEDED, user.getUsername(), "password");
                return issueSession(user, false);
        }

        private AuthResponse adminSecondFactor(User user, String otp) {
                requireMfaConfigured();
                if (!user.isMfaEnabled()) {
                        log.info("Admin {} signed in with password; MFA enrolment required", user.getUsername());
                        return AuthResponse.enrolmentRequired(jwtService.generateEnrolmentToken(user.getUsername()),
                                        0, user.getUsername(), user.getEmail(), user.getRole());
                }
                if (otp == null || otp.isBlank()) {
                        throw AuthFlowException.mfaRequired();
                }
                if (!acceptOtp(user, otp)) {
                        registerFailedAttempt(user);   // counts toward the same lockout as a wrong password
                        log.warn("Failed login attempt (wrong one-time code) for admin: {}", user.getUsername());
                        audit.record(user.getUsername(), AuditActions.MFA_CODE_FAILED, user.getUsername(), null);
                        throw AuthFlowException.invalidOtp();
                }
                clearFailedAttempts(user);
                log.info("Admin {} signed in with MFA", user.getUsername());
                audit.record(user.getUsername(), AuditActions.LOGIN_SUCCEEDED, user.getUsername(), "password+otp");
                return issueSession(user, true);
        }

        /** Step 1 of MFA setup: a new secret for the authenticator app. Repeatable until confirmed. */
        public MfaEnrolmentResponse startMfaEnrolment(String enrolmentToken) {
                requireMfaConfigured();
                User user = enrollingAdmin(enrolmentToken);

                byte[] secret = Totp.newSecret();
                user.setMfaSecret(mfaSecretCipher.encrypt(secret, user.getId()));
                userRepository.save(user);

                log.info("MFA enrolment started for admin {}", user.getUsername());
                return new MfaEnrolmentResponse(Totp.base32(secret), Totp.otpauthUri(TOTP_ISSUER, user.getUsername(), secret));
        }

        /** Step 2: a code from the app proves it was set up; MFA is switched on and an admin session starts. */
        public AuthResponse confirmMfaEnrolment(String enrolmentToken, String otp) {
                requireMfaConfigured();
                User user = enrollingAdmin(enrolmentToken);
                if (user.getMfaSecret() == null) {
                        throw new AuthFlowException(HttpStatus.BAD_REQUEST, "MFA_ENROLMENT_NOT_STARTED",
                                        "Start MFA enrolment first");
                }
                if (!acceptOtp(user, otp)) {
                        throw AuthFlowException.invalidOtp();
                }
                user.setMfaEnabled(true);
                clearFailedAttempts(user);
                userRepository.save(user);

                log.info("MFA enabled for admin {}", user.getUsername());
                audit.record(user.getUsername(), AuditActions.MFA_ENROLLED, user.getUsername(), null);
                return issueSession(user, true);
        }

        private User enrollingAdmin(String enrolmentToken) {
                String username = jwtService.validateEnrolmentToken(enrolmentToken);
                User user = username == null ? null : userRepository.findByUsername(username).orElse(null);
                if (user == null || !user.isAdmin()) {
                        throw new AuthFlowException(HttpStatus.UNAUTHORIZED, "INVALID_ENROLMENT_TOKEN",
                                        "Invalid or expired enrolment token - sign in again");
                }
                if (user.isMfaEnabled()) {
                        throw new AuthFlowException(HttpStatus.CONFLICT, "MFA_ALREADY_ENABLED", "MFA is already enabled");
                }
                return user;
        }

        /** Valid code for this user's secret, not used before (atomically recorded as used). */
        private boolean acceptOtp(User user, String otp) {
                byte[] secret = mfaSecretCipher.decrypt(user.getMfaSecret(), user.getId());
                OptionalLong step = Totp.verify(secret, otp, clock.instant(), user.getMfaLastStep());
                return step.isPresent() && userRepository.claimMfaStep(user.getId(), step.getAsLong()) == 1;
        }

        private void requireMfaConfigured() {
                if (!mfaSecretCipher.isConfigured()) {
                        log.error("Admin sign-in refused: raillens.mfa.encryption-key is not configured");
                        throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "MFA_NOT_CONFIGURED",
                                        "Admin sign-in is not configured on this server");
                }
        }

        private AuthResponse issueSession(User user, boolean mfa) {
                String token = jwtService.generateToken(user, mfa);
                String refreshToken = refreshTokenService.issue(user, mfa);
                return AuthResponse.of(token, jwtService.getExpirationSeconds(user, mfa), refreshToken,
                                user.getUsername(), user.getEmail(), user.getRole());
        }

        private String dummyPasswordHash() {
                String hash = dummyPasswordHash;
                if (hash == null) {
                        hash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
                        dummyPasswordHash = hash;
                }
                return hash;
        }

        /** A successful sign-in: the failure count and the lockout streak both start again. */
        private void clearFailedAttempts(User user) {
                if (user.getFailedLoginAttempts() != 0 || user.getLockoutCount() != 0) {
                        user.setFailedLoginAttempts(0);
                        user.setLockoutCount(0);
                        userRepository.save(user);
                }
        }

        /**
         * Phase 6, progressive lockout: the first lockout lasts lockout-duration-minutes, each further one in a row
         * (no successful sign-in in between) twice as long, up to lockout-max-minutes. Trade-off: someone who knows a
         * username can keep that account locked for longer; the per-IP sign-in rate limit keeps that slow.
         */
        long lockoutMinutes(int lockoutsInARow) {
                long minutes = lockoutDurationMinutes;
                for (int i = 1; i < lockoutsInARow && minutes < lockoutMaxMinutes; i++) {
                        minutes *= 2;
                }
                return Math.min(minutes, Math.max(lockoutDurationMinutes, lockoutMaxMinutes));
        }

        private void registerFailedAttempt(User user) {

                int attempts = user.getFailedLoginAttempts() + 1;
                user.setFailedLoginAttempts(attempts);

                if (attempts >= maxFailedLoginAttempts) {
                        int streak = user.getLockoutCount() + 1;
                        long minutes = lockoutMinutes(streak);
                        user.setLockoutCount(streak);
                        user.setLockedUntil(LocalDateTime.now().plusMinutes(minutes));
                        log.warn("Account locked for {} minutes (lockout {} in a row) after {} failed login attempts: {}",
                                        minutes, streak, attempts, user.getUsername());
                        audit.record(user.getUsername(), AuditActions.ACCOUNT_LOCKED, user.getUsername(),
                                        minutes + " min, lockout " + streak + " in a row");
                }

                userRepository.save(user);
        }

        /**
         * The role is re-read from the database on every refresh (a demoted admin's next token is a user token), and
         * the MFA flag comes from the session's refresh-token family, never from the client.
         */
        public AuthResponse refresh(String rawRefreshToken) {

                RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(rawRefreshToken);
                User user = rotated.user();
                boolean mfa = rotated.mfa();

                return AuthResponse.of(jwtService.generateToken(user, mfa), jwtService.getExpirationSeconds(user, mfa),
                                rotated.rawToken(), user.getUsername(), user.getEmail(), user.getRole());
        }

        public void logout(String rawRefreshToken) {
                refreshTokenService.revoke(rawRefreshToken);
        }

        public CurrentUserResponse getCurrentUser(String username) {

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));

                return new CurrentUserResponse(user.getUsername(), user.getEmail(), user.getCreatedAt(), user.getRole(),
                                user.isMfaEnabled());
        }

        /**
         * {@code railOpsToken} is the caller's RailOps access token when they signed in with the RailOps login
         * (single login, Phase 4D), otherwise null. A linked profile's password lives in RailOps, so the change is
         * forwarded there; the old RailLens copy is cleared in the same transaction so it can't be used again.
         */
        @Transactional
        public void changePassword(String username, ChangePasswordRequest request, String railOpsToken) {

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));

                if (user.getRailopsUserId() != null) {
                        if (railOpsToken == null) {
                                throw managedByRailOps();
                        }
                        // Local first and flushed, so a database problem shows up before RailOps changes anything.
                        user.setPasswordHash(null);
                        userRepository.saveAndFlush(user);
                        refreshTokenService.revokeAllForUser(user);
                        railOpsAccounts.changePassword(railOpsToken, request.currentPassword(), request.newPassword());
                        log.info("Password changed in RailOps for linked user: {}", username);
                        audit.record(username, AuditActions.PASSWORD_CHANGED, username, "in RailOps");
                        return;
                }

                if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                        throw new InvalidCredentialsException("Current password is incorrect");
                }

                user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
                userRepository.save(user);

                refreshTokenService.revokeAllForUser(user);

                log.info("Password changed for user: {}", username);
                audit.record(username, AuditActions.PASSWORD_CHANGED, username, null);
        }

        /**
         * A linked profile is closed together with its RailOps login: the local delete is flushed first, then RailOps
         * checks the password and erases its account; if RailOps refuses, the local delete rolls back.
         */
        @Transactional
        public void deleteAccount(String username, String password, String railOpsToken) {

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));

                if (user.getRailopsUserId() != null) {
                        if (railOpsToken == null) {
                                throw managedByRailOps();
                        }
                        refreshTokenService.deleteAllForUser(user);
                        userRepository.delete(user);
                        userRepository.flush();
                        railOpsAccounts.erase(railOpsToken, password);
                        log.info("Deleted linked account together with its RailOps login: {}", username);
                        audit.record(username, AuditActions.ACCOUNT_DELETED, username, "with RailOps login");
                        return;
                }

                if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                        throw new InvalidCredentialsException("Password is incorrect");
                }

                refreshTokenService.deleteAllForUser(user);
                userRepository.delete(user);

                log.info("Deleted account: {}", username);
                audit.record(username, AuditActions.ACCOUNT_DELETED, username, null);
        }

        private static AuthFlowException managedByRailOps() {
                return new AuthFlowException(HttpStatus.CONFLICT, "MANAGED_BY_RAILOPS",
                                "This account uses the RailOps sign-in. Sign in with RailOps to change it");
        }
}
