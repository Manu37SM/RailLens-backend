package com.labs.train.train_db.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.labs.train.train_db.entity.User;
import com.labs.train.train_db.exception.DuplicateUserException;
import com.labs.train.train_db.exception.InvalidCredentialsException;
import com.labs.train.train_db.model.AuthResponse;
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

                return AuthResponse.of(token, jwtService.getExpirationSeconds(), user.getUsername(), user.getEmail());
        }

        public AuthResponse login(LoginRequest request) {

                User user = userRepository
                                .findByUsernameOrEmail(request.usernameOrEmail(), request.usernameOrEmail())
                                .orElseThrow(() -> new InvalidCredentialsException("Invalid username/email or password"));

                if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                        throw new InvalidCredentialsException("Invalid username/email or password");
                }

                String token = jwtService.generateToken(user.getUsername());

                return AuthResponse.of(token, jwtService.getExpirationSeconds(), user.getUsername(), user.getEmail());
        }

        public CurrentUserResponse getCurrentUser(String username) {

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));

                return new CurrentUserResponse(user.getUsername(), user.getEmail(), user.getCreatedAt());
        }
}
