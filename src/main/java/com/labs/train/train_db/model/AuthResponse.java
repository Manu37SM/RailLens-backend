package com.labs.train.train_db.model;

public record AuthResponse(

                String token,

                String tokenType,

                long expiresInSeconds,

                // Opaque, long-lived - exchange it at POST /api/auth/refresh for a
                // new access token (and a new refreshToken - see
                // RefreshTokenService's rotation model) once this one expires,
                // instead of asking the user to log in again.
                String refreshToken,

                String username,

                String email) {

        public static AuthResponse of(
                        String token,
                        long expiresInSeconds,
                        String refreshToken,
                        String username,
                        String email) {
                return new AuthResponse(token, "Bearer", expiresInSeconds, refreshToken, username, email);
        }
}
