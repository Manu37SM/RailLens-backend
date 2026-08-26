package com.labs.train.train_db.model;

public record AuthResponse(

                String token,

                String tokenType,

                long expiresInSeconds,

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
