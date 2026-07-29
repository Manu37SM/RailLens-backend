package com.labs.train.train_db.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.labs.train.train_db.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // Login accepts either a username or an email in the same field (see
    // LoginRequest) - this is the single query that backs that lookup.
    Optional<User> findByUsernameOrEmail(String username, String email);
}
