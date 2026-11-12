package com.labs.train.train_db.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Login/register/refresh result. Fields after {@code email} were added in V7 (admin MFA) and are additive: existing
 * clients ignore them.
 *
 * <p>When {@code mfaEnrolmentRequired} is true, the password was right but the account is an admin without MFA:
 * {@code token} and {@code refreshToken} are null and {@code enrolmentToken} starts /api/v1/auth/mfa/enrol.
 */
public record AuthResponse(

                String token,

                String tokenType,

                long expiresInSeconds,

                String refreshToken,

                String username,

                String email,

                String role,

                boolean mfaEnrolmentRequired,

                @JsonInclude(JsonInclude.Include.NON_NULL)
                String enrolmentToken) {

        public static AuthResponse of(
                        String token,
                        long expiresInSeconds,
                        String refreshToken,
                        String username,
                        String email,
                        String role) {
                return new AuthResponse(token, "Bearer", expiresInSeconds, refreshToken, username, email, role, false, null);
        }

        public static AuthResponse enrolmentRequired(String enrolmentToken, long expiresInSeconds, String username,
                        String email, String role) {
                return new AuthResponse(null, "Bearer", expiresInSeconds, null, username, email, role, true, enrolmentToken);
        }
}
