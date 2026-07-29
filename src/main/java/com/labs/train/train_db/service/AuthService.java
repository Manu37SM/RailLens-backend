package com.labs.train.train_db.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.labs.train.train_db.entity.User;
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

/**
 * Business logic for registration and login. Plaintext passwords never
 * leave this class - they come in on the request DTO, go through {@link
 * PasswordEncoder}, and only the BCrypt hash is ever persisted or logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final RefreshTokenService refreshTokenService;

        public AuthResponse register(RegisterRequest request) {

                // Checked explicitly (rather than relying solely on the unique
                // DB constraint) so the caller gets a specific "username taken"
                // vs. "email taken" message instead of a generic conflict.
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
                                .orElseThrow(() -> new InvalidCredentialsException("Invalid username/email or password"));

                if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                        throw new InvalidCredentialsException("Invalid username/email or password");
                }

                String token = jwtService.generateToken(user.getUsername());
                String refreshToken = refreshTokenService.issue(user);

                return AuthResponse.of(
                                token, jwtService.getExpirationSeconds(), refreshToken, user.getUsername(), user.getEmail());
        }

        /**
         * Exchanges a valid refresh token for a fresh access token and a
         * rotated refresh token (see RefreshTokenService.rotate). Doesn't need
         * the caller to already hold a valid access JWT - that's the whole
         * point of a refresh token existing.
         */
        public AuthResponse refresh(String rawRefreshToken) {

                RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(rawRefreshToken);
                User user = rotated.user();

                String token = jwtService.generateToken(user.getUsername());

                return AuthResponse.of(
                                token, jwtService.getExpirationSeconds(), rotated.rawToken(), user.getUsername(), user.getEmail());
        }

        /**
         * Revokes a single refresh token so it can no longer be exchanged for
         * a new access token - called on explicit logout. Doesn't touch the
         * still-live access JWT (those are stateless and simply expire on
         * their own short timer), only the long-lived refresh credential.
         */
        public void logout(String rawRefreshToken) {
                refreshTokenService.revoke(rawRefreshToken);
        }

        public CurrentUserResponse getCurrentUser(String username) {

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));

                return new CurrentUserResponse(user.getUsername(), user.getEmail(), user.getCreatedAt());
        }

        /**
         * Requires the current password before setting a new one (checked
         * here, not just relying on the caller already holding a valid JWT) -
         * a bearer token alone shouldn't be enough to lock the real owner out
         * of their own account if it leaked from, say, a shared/public
         * computer.
         */
        public void changePassword(String username, ChangePasswordRequest request) {

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));

                if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                        throw new InvalidCredentialsException("Current password is incorrect");
                }

                user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
                userRepository.save(user);

                // A changed password should invalidate every other session, not
                // just stop working for new logins - otherwise a refresh token
                // issued before the change would keep minting valid access
                // tokens forever, defeating the point of changing the password.
                refreshTokenService.revokeAllForUser(user);

                log.info("Password changed for user: {}", username);
        }

        /**
         * Permanently deletes the account. Favorites/search history/recent
         * searches live in the browser's localStorage (see
         * stores/favoritesStore.ts etc. on the frontend), not this table, so
         * there's no related data here to cascade-delete - this is a single
         * row removal.
         */
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
