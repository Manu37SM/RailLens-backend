package com.labs.train.train_db.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.labs.train.train_db.entity.User;
import com.labs.train.train_db.exception.AccountLockedException;
import com.labs.train.train_db.exception.DuplicateUserException;
import com.labs.train.train_db.exception.InvalidCredentialsException;
import com.labs.train.train_db.model.AuthResponse;
import com.labs.train.train_db.model.ChangePasswordRequest;
import com.labs.train.train_db.model.CurrentUserResponse;
import com.labs.train.train_db.model.LoginRequest;
import com.labs.train.train_db.model.RegisterRequest;
import com.labs.train.train_db.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final RefreshTokenService refreshTokenService;

        @Value("${raillens.auth.max-failed-login-attempts:5}")
        private int maxFailedLoginAttempts;

        @Value("${raillens.auth.lockout-duration-minutes:15}")
        private long lockoutDurationMinutes;

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

                String token = jwtService.generateToken(user.getUsername());
                String refreshToken = refreshTokenService.issue(user);

                return AuthResponse.of(
                                token, jwtService.getExpirationSeconds(), refreshToken, user.getUsername(), user.getEmail());
        }

        public AuthResponse login(LoginRequest request) {

                User user = userRepository
                                .findByUsernameOrEmail(request.usernameOrEmail(), request.usernameOrEmail())
                                .orElseThrow(() -> {
                                        log.warn("Failed login attempt for unknown username/email: {}", request.usernameOrEmail());
                                        return new InvalidCredentialsException("Invalid username/email or password");
                                });

                if (user.getLockedUntil() != null) {
                        if (user.getLockedUntil().isAfter(LocalDateTime.now())) {
                                throw new AccountLockedException(
                                                "Account temporarily locked due to too many failed login attempts. "
                                                                + "Try again later.");
                        }

                        user.setLockedUntil(null);
                        user.setFailedLoginAttempts(0);
                }

                if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                        registerFailedAttempt(user);
                        log.warn("Failed login attempt (wrong password) for user: {}", user.getUsername());
                        throw new InvalidCredentialsException("Invalid username/email or password");
                }

                if (user.getFailedLoginAttempts() != 0) {
                        user.setFailedLoginAttempts(0);
                        userRepository.save(user);
                }

                String token = jwtService.generateToken(user.getUsername());
                String refreshToken = refreshTokenService.issue(user);

                return AuthResponse.of(
                                token, jwtService.getExpirationSeconds(), refreshToken, user.getUsername(), user.getEmail());
        }

        private void registerFailedAttempt(User user) {

                int attempts = user.getFailedLoginAttempts() + 1;
                user.setFailedLoginAttempts(attempts);

                if (attempts >= maxFailedLoginAttempts) {
                        user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutDurationMinutes));
                        log.warn(
                                        "Account locked for {} minutes after {} failed login attempts: {}",
                                        lockoutDurationMinutes, attempts, user.getUsername());
                }

                userRepository.save(user);
        }

        public AuthResponse refresh(String rawRefreshToken) {

                RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(rawRefreshToken);
                User user = rotated.user();

                String token = jwtService.generateToken(user.getUsername());

                return AuthResponse.of(
                                token, jwtService.getExpirationSeconds(), rotated.rawToken(), user.getUsername(), user.getEmail());
        }

        public void logout(String rawRefreshToken) {
                refreshTokenService.revoke(rawRefreshToken);
        }

        public CurrentUserResponse getCurrentUser(String username) {

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));

                return new CurrentUserResponse(user.getUsername(), user.getEmail(), user.getCreatedAt());
        }

        public void changePassword(String username, ChangePasswordRequest request) {

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));

                if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                        throw new InvalidCredentialsException("Current password is incorrect");
                }

                user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
                userRepository.save(user);

                refreshTokenService.revokeAllForUser(user);

                log.info("Password changed for user: {}", username);
        }

        public void deleteAccount(String username, String password) {

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));

                if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                        throw new InvalidCredentialsException("Password is incorrect");
                }

                refreshTokenService.deleteAllForUser(user);
                userRepository.delete(user);

                log.info("Deleted account: {}", username);
        }
}
