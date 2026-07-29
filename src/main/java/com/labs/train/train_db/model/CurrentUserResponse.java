package com.labs.train.train_db.model;

import java.time.LocalDateTime;

public record CurrentUserResponse(

                String username,

                String email,

                LocalDateTime createdAt) {
}
