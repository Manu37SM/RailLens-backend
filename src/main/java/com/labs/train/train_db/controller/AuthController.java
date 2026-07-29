package com.labs.train.train_db.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.AuthResponse;
import com.labs.train.train_db.model.CurrentUserResponse;
import com.labs.train.train_db.model.LoginRequest;
import com.labs.train.train_db.model.RegisterRequest;
import com.labs.train.train_db.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Registration and login for the (currently view-only) frontend. Tokens are
 * plain bearer JWTs (see {@code JwtService}) - the frontend is expected to
 * store the token and send it as {@code Authorization: Bearer <token>} on
 * any future request that needs it. Nothing currently reads that header
 * except {@code /api/auth/me}; write endpoints on trains/stations/schedules
 * remain unauthenticated pending a separate decision (see project memory).
 */
@RestController
@RequestMapping("/api/auth")
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

        /**
         * The authenticated username is resolved and attached to the request
         * by {@code JwtAuthInterceptor} before this method runs; a missing or
         * invalid token never reaches here (the interceptor rejects it with
         * 401 first).
         */
        @GetMapping("/me")
        public ResponseEntity<CurrentUserResponse> me(@RequestAttribute("authenticatedUsername") String username) {
                return ResponseEntity.ok(authService.getCurrentUser(username));
        }
}
