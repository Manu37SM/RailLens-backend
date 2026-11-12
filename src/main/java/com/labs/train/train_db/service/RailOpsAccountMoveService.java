package com.labs.train.train_db.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The RailLens half of moving accounts to the single RailOps login (Phase 4C). RailOps' identity service pulls the
 * accounts that still need moving, creates them on its side (same email, same bcrypt password hash, so nobody's
 * password changes) and sends back the new RailOps id for each, which is stored as the link Phase 4B trusts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RailOpsAccountMoveService {

        public static final int MAX_PAGE = 500;

        /** One account to move. passwordHash is a bcrypt hash: sensitive, admin + MFA only, never cached. */
        public record AccountToMove(long raillensUserId, String email, String passwordHash, boolean admin) {
        }

        public record Link(long raillensUserId, UUID railopsUserId) {
        }

        public record LinkResult(int linked, int alreadyLinked, List<Long> conflicts) {
        }

        private final JdbcClient jdbc;

        /** Accounts with a RailLens password and no link yet, after {@code afterId}, oldest first. */
        public List<AccountToMove> pending(long afterId, int limit) {
                int size = Math.max(1, Math.min(limit, MAX_PAGE));
                return jdbc.sql("""
                                SELECT id, email, password_hash, role FROM users
                                WHERE railops_user_id IS NULL AND password_hash IS NOT NULL AND id > :after
                                ORDER BY id LIMIT :size
                                """)
                                .param("after", afterId).param("size", size)
                                .query((rs, n) -> new AccountToMove(rs.getLong("id"), rs.getString("email"),
                                                rs.getString("password_hash"), "ADMIN".equals(rs.getString("role"))))
                                .list();
        }

        /**
         * Stores each link once. An account that is already linked is never re-pointed, and a RailOps id that is
         * already used by another account is refused (reported as a conflict).
         */
        public LinkResult link(List<Link> links) {
                int linked = 0;
                int already = 0;
                List<Long> conflicts = new ArrayList<>();
                for (Link l : links) {
                        try {
                                int n = jdbc.sql("UPDATE users SET railops_user_id = :r WHERE id = :id AND railops_user_id IS NULL")
                                                .param("r", l.railopsUserId()).param("id", l.raillensUserId()).update();
                                if (n == 1) {
                                        linked++;
                                        continue;
                                }
                                UUID current = jdbc.sql("SELECT railops_user_id FROM users WHERE id = :id")
                                                .param("id", l.raillensUserId()).query(UUID.class).optional().orElse(null);
                                if (l.railopsUserId().equals(current)) {
                                        already++;
                                } else {
                                        conflicts.add(l.raillensUserId());
                                }
                        } catch (DataIntegrityViolationException e) {
                                conflicts.add(l.raillensUserId());   // that RailOps id belongs to another account
                        }
                }
                log.info("RailOps account links stored: {} new, {} already, {} conflicts", linked, already, conflicts.size());
                return new LinkResult(linked, already, conflicts);
        }
}
