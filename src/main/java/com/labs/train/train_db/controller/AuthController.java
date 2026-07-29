package com.labs.train.train_db.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.AuthResponse;
import com.labs.train.train_db.model.ChangePasswordRequest;
import com.labs.train.train_db.model.CurrentUserResponse;
import com.labs.train.train_db.model.DeleteAccountRequest;
import com.labs.train.train_db.model.LoginRequest;
import com.labs.train.train_db.model.RefreshRequest;
import com.labs.train.train_db.model.RegisterRequest;
import com.labs.train.train_db.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Registration, login and account management for the frontend. Tokens are
 * plain bearer JWTs (see {@code JwtService}) - the frontend is expected to
 * store the token and send it as {@code Authorization: Bearer <token>} on
 * any request that needs one. Write endpoints on trains/stations/schedules
 * remain unauthenticated pending a separate decision (see project memory).
 *
 * Every method below except register/login/refresh/logout requires a
 * valid access token - {@code JwtAuthInterceptor} is registered against
 * all of {@code /api/auth/**} except those four (see WebConfig), resolves
 * the authenticated username, and attaches it as a request attribute
 * before the method runs; a missing/invalid token never reaches here.
 * refresh/logout are deliberately excluded too since their whole purpose
 * is to work with a refresh token instead of - often specifically
 * because there is no longer - a valid access token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

        private final AuthService authService;

        @PostMapping("/register")
        public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
                return ResponseEntity.ok(authService.register(request));
        }

        @PostMapping("/login")
        public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
                return ResponseEntity.ok(authService.login(request));
        }

        @PostMapping("/refresh")
        public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
                return ResponseEntity.ok(authService.refresh(request.refreshToken()));
        }

        @PostMapping("/logout")
        public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
                authService.logout(request.refreshToken());
                return ResponseEntity.noContent().build();
        }

        @GetMapping("/me")
        public ResponseEntity<CurrentUserResponse> me(@RequestAttribute("authenticatedUsername") String username) {
                return ResponseEntity.ok(authService.getCurrentUser(username));
        }

        @PutMapping("/password")
        public ResponseEntity<Void> changePassword(
                        @RequestAttribute("authenticatedUsername") String username,
                        @Valid @RequestBody ChangePasswordRequest request) {

                authService.changePassword(username, request);
                return ResponseEntity.noContent().build();
        }

        /**
         * Permanent - see AuthService#deleteAccount. Requires the current
         * password in the body even though the request is already
         * token-authenticated (see that method's javadoc for why).
         */
        @DeleteMapping("/me")
        public ResponseEntity<Void> deleteAccount(
                        @RequestAttribute("authenticatedUsername") String username,
                        @Valid @RequestBody DeleteAccountRequest request) {

                authService.deleteAccount(username, request.password());
                return ResponseEntity.noContent().build();
        }
}
