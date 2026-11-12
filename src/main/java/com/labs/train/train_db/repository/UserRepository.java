package com.labs.train.train_db.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    /**
     * Records a used TOTP step only if it is newer than the last one: 1 = accepted, 0 = replay (another login used
     * this code, or a later one, first). A single conditional UPDATE, so two simultaneous logins with the same code
     * can't both win.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update User u set u.mfaLastStep = :step where u.id = :id and (u.mfaLastStep is null or u.mfaLastStep < :step)")
    int claimMfaStep(@Param("id") Long id, @Param("step") long step);
}
