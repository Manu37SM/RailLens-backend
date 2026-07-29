package com.labs.train.train_db.model;

public record AuthResponse(

                String token,

                String tokenType,

                long expiresInSeconds,

                String username,

                String email) {

        public static AuthResponse of(String token, long expiresInSeconds, String username, String email) {
                return new AuthResponse(token, "Bearer", expiresInSeconds, username, email);
        }
}
