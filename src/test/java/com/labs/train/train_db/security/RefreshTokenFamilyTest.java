package com.labs.train.train_db.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.labs.train.train_db.entity.User;
import com.labs.train.train_db.exception.InvalidCredentialsException;
import com.labs.train.train_db.repository.UserRepository;
import com.labs.train.train_db.service.RefreshTokenService;

/** Refresh-token families on real PostgreSQL: what mocks can't show (commit despite exception, row locks). */
@SpringBootTest(properties = "raillens.admin.api-key=unused")
class RefreshTokenFamilyTest {

        @Autowired
        private RefreshTokenService tokens;

        @Autowired
        private UserRepository users;

        @Autowired
        private JdbcClient jdbc;

        private User newUser() {
                String name = "f" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                User u = new User();
                u.setUsername(name);
                u.setEmail(name + "@example.com");
                u.setPasswordHash("x");
                return users.save(u);
        }

        private void ageRotation(String rawOrNull, User user) {
                // Push every rotation of this user's tokens outside the 10 s grace window.
                jdbc.sql("UPDATE refresh_tokens SET rotated_at = rotated_at - interval '1 hour' "
                                + "WHERE user_id = :u AND rotated_at IS NOT NULL").param("u", user.getId()).update();
        }

        @Test
        void aReplayedRotatedTokenRevokesTheFamilyAndTheRevocationCommits() {
                User user = newUser();
                String original = tokens.issue(user);
                String next = tokens.rotate(original).rawToken();   // the user's legitimate refresh
                ageRotation(original, user);

                // The attacker replays the stolen original. The call fails, AND the family revocation must persist
                // even though the transaction ends in an exception (noRollbackFor).
                assertThatThrownBy(() -> tokens.rotate(original)).isInstanceOf(InvalidCredentialsException.class);

                assertThatThrownBy(() -> tokens.rotate(next))
                                .as("the user's current token died with the family: forced re-login")
                                .isInstanceOf(InvalidCredentialsException.class);
                assertThat(jdbc.sql("SELECT count(*) FROM refresh_tokens WHERE user_id = :u AND NOT revoked")
                                .param("u", user.getId()).query(Long.class).single()).isZero();
        }

        @Test
        void otherSessionsOfTheSameUserSurviveATheftInOneFamily() {
                User user = newUser();
                String phone = tokens.issue(user);
                String laptop = tokens.issue(user);
                tokens.rotate(phone);
                ageRotation(phone, user);
                assertThatThrownBy(() -> tokens.rotate(phone)).isInstanceOf(InvalidCredentialsException.class);

                assertThat(tokens.rotate(laptop).rawToken()).as("a separate login is a separate family").isNotBlank();
        }

        @Test
        void concurrentRefreshesWithOneTokenYieldExactlyOneSuccessor() throws Exception {
                User user = newUser();
                String raw = tokens.issue(user);
                int threads = 6;
                ExecutorService pool = Executors.newFixedThreadPool(threads);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                        results.add(pool.submit(() -> {
                                start.await();
                                try {
                                        tokens.rotate(raw);
                                        return true;
                                } catch (InvalidCredentialsException e) {
                                        return false;
                                }
                        }));
                }
                start.countDown();
                int ok = 0;
                for (Future<Boolean> f : results) {
                        ok += f.get(30, TimeUnit.SECONDS) ? 1 : 0;
                }
                pool.shutdown();
                assertThat(ok).as("without the row lock every thread could rotate the same token").isEqualTo(1);
                assertThat(jdbc.sql("SELECT count(*) FROM refresh_tokens WHERE user_id = :u AND NOT revoked")
                                .param("u", user.getId()).query(Long.class).single()).isEqualTo(1);
                assertThat(jdbc.sql("SELECT count(*) FROM refresh_tokens WHERE user_id = :u AND NOT revoked AND family_id = "
                                + "(SELECT family_id FROM refresh_tokens WHERE user_id = :u LIMIT 1)")
                                .param("u", user.getId()).query(Long.class).single())
                                .as("racing tabs inside the grace window do not trigger a theft alarm").isEqualTo(1);
        }

        @Test
        void cleanupKeepsRotatedTokensAsEvidenceButDropsExpiredAndLoggedOutOnes() {
                User user = newUser();
                String rotated = tokens.issue(user);
                tokens.rotate(rotated);
                String loggedOut = tokens.issue(user);
                tokens.revoke(loggedOut);
                jdbc.sql("INSERT INTO refresh_tokens (user_id, token_hash, expires_at, revoked, created_at) "
                                + "VALUES (:u, :h, :e, false, now())")   // written the way the OLD code wrote rows
                                .param("u", user.getId()).param("h", "e".repeat(64))
                                .param("e", LocalDateTime.now().minusDays(1)).update();

                tokens.purgeExpiredAndLoggedOut();

                List<String> left = jdbc.sql("SELECT CASE WHEN rotated_at IS NOT NULL THEN 'rotated' "
                                + "WHEN revoked THEN 'logged-out' ELSE 'live' END FROM refresh_tokens WHERE user_id = :u ORDER BY id")
                                .param("u", user.getId()).query(String.class).list();
                assertThat(left).containsExactly("rotated", "live");
        }
}
