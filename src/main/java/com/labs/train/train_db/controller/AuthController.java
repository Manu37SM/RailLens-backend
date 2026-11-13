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
import com.labs.train.train_db.model.MfaConfirmRequest;
import com.labs.train.train_db.model.MfaEnrolRequest;
import com.labs.train.train_db.model.MfaEnrolmentResponse;
import com.labs.train.train_db.model.RefreshRequest;
import com.labs.train.train_db.model.RegisterRequest;
import com.labs.train.train_db.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

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

        /** Admin MFA setup, step 1: secret + otpauth URI for the authenticator app. */
        @PostMapping("/mfa/enrol")
        public ResponseEntity<MfaEnrolmentResponse> startMfaEnrolment(@Valid @RequestBody MfaEnrolRequest request) {
                return ResponseEntity.ok(authService.startMfaEnrolment(request.enrolmentToken()));
        }

        /** Step 2: first code from the app; enables MFA and returns an admin session. */
        @PostMapping("/mfa/confirm")
        public ResponseEntity<AuthResponse> confirmMfaEnrolment(@Valid @RequestBody MfaConfirmRequest request) {
                return ResponseEntity.ok(authService.confirmMfaEnrolment(request.enrolmentToken(), request.otp()));
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
                        @RequestAttribute(name = "railOpsAccessToken", required = false) String railOpsToken,
                        @Valid @RequestBody ChangePasswordRequest request) {

                authService.changePassword(username, request, railOpsToken);
                return ResponseEntity.noContent().build();
        }

        @DeleteMapping("/me")
        public ResponseEntity<Void> deleteAccount(
                        @RequestAttribute("authenticatedUsername") String username,
                        @RequestAttribute(name = "railOpsAccessToken", required = false) String railOpsToken,
                        @Valid @RequestBody DeleteAccountRequest request) {

                authService.deleteAccount(username, request.password(), railOpsToken);
                return ResponseEntity.noContent().build();
        }
}
