package com.labs.train.train_db.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.labs.train.train_db.config.ClientIpResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * Tamper-evident security audit log (Phase 6). Every entry's hash covers the previous entry's hash, so editing,
 * deleting or reordering an old entry breaks the chain from that point on; {@link #verify()} finds where.
 *
 * <p>Appends are serialised with a transaction-scoped advisory lock (one chain, no forks) and run in their own
 * transaction, so a failed login is recorded even though the login itself fails. An audit write that fails is
 * logged and never breaks the user's request: losing an audit line is bad, refusing every login because of it is
 * worse.
 *
 * <p>What it records is deliberately small: usernames and client IPs; never emails, passwords, codes or tokens.
 */
@Slf4j
@Service
public class SecurityAuditService {

        static final String GENESIS = "0".repeat(64);
        private static final long LOCK_KEY = 0x5241494C4C454E53L;   // "RAILLENS"

        public record Entry(long id, Instant occurredAt, String actor, String action, String target, String detail,
                        String clientIp, String prevHash, String hash) {
        }

        public record Verification(boolean intact, long checked, Long firstBrokenId) {
        }

        private final JdbcClient jdbc;
        private final ClientIpResolver clientIps;
        private final TransactionTemplate ownTransaction;

        public SecurityAuditService(JdbcClient jdbc, ClientIpResolver clientIps, PlatformTransactionManager tx) {
                this.jdbc = jdbc;
                this.clientIps = clientIps;
                this.ownTransaction = new TransactionTemplate(tx);
                this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        public void record(String actor, String action, String target, String detail) {
                String ip = currentClientIp();   // read on the request thread, outside the new transaction
                try {
                        ownTransaction.executeWithoutResult(status -> append(actor, action, target, detail, ip));
                } catch (RuntimeException e) {
                        log.error("Security audit write failed for action {}: {}", action, e.getClass().getSimpleName());
                }
        }

        private void append(String actor, String action, String target, String detail, String ip) {
                {
                        jdbc.sql("SELECT pg_advisory_xact_lock(:k)").param("k", LOCK_KEY).query(Object.class).optional();
                        String prev = jdbc.sql("SELECT hash FROM security_audit ORDER BY id DESC LIMIT 1")
                                        .query(String.class).optional().orElse(GENESIS);
                        // Microseconds: what PostgreSQL stores, so the hash recomputes exactly when verified.
                        Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
                        String a = clip(actor, 100), ac = clip(action, 60), t = clip(target, 100), d = clip(detail, 500);
                        String hash = hash(prev, at, a, ac, t, d, ip);
                        jdbc.sql("""
                                        INSERT INTO security_audit (occurred_at, actor, action, target, detail, client_ip, prev_hash, hash)
                                        VALUES (:at, :actor, :action, :target, :detail, :ip, :prev, :hash)
                                        """)
                                        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC)).param("actor", a).param("action", ac)
                                        .param("target", t).param("detail", d).param("ip", ip).param("prev", prev).param("hash", hash)
                                        .update();
                }
        }

        @Transactional(readOnly = true)
        public List<Entry> page(long afterId, int limit) {
                return jdbc.sql("""
                                SELECT id, occurred_at, actor, action, target, detail, client_ip, prev_hash, hash
                                FROM security_audit WHERE id > :after ORDER BY id LIMIT :limit
                                """)
                                .param("after", afterId).param("limit", Math.clamp(limit, 1, 500))
                                .query((rs, n) -> new Entry(rs.getLong("id"), rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                                                rs.getString("actor"), rs.getString("action"), rs.getString("target"), rs.getString("detail"),
                                                rs.getString("client_ip"), rs.getString("prev_hash"), rs.getString("hash")))
                                .list();
        }

        /** Walks the whole chain in id order, in pages. */
        @Transactional(readOnly = true)
        public Verification verify() {
                String expectedPrev = GENESIS;
                long checked = 0;
                long after = 0;
                while (true) {
                        List<Entry> batch = page(after, 500);
                        if (batch.isEmpty()) {
                                return new Verification(true, checked, null);
                        }
                        for (Entry e : batch) {
                                String recomputed = hash(e.prevHash(), e.occurredAt(), e.actor(), e.action(), e.target(), e.detail(),
                                                e.clientIp());
                                if (!e.prevHash().equals(expectedPrev) || !recomputed.equals(e.hash())) {
                                        return new Verification(false, checked, e.id());
                                }
                                expectedPrev = e.hash();
                                checked++;
                                after = e.id();
                        }
                }
        }

        /** Length-prefixed fields, so "ab"+"c" and "a"+"bc" can never hash the same. */
        static String hash(String prev, Instant at, String actor, String action, String target, String detail, String ip) {
                StringBuilder sb = new StringBuilder();
                for (String f : new String[] { prev, at.toString(), actor, action, target, detail, ip }) {
                        String v = f == null ? "" : f;
                        sb.append(f == null ? -1 : v.length()).append(':').append(v).append('|');
                }
                try {
                        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                                        .digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
                } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException(e);
                }
        }

        private String currentClientIp() {
                if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                        return clip(clientIps.resolve(attrs.getRequest()), 45);
                }
                return null;
        }

        private static String clip(String s, int max) {
                if (s == null) {
                        return null;
                }
                String clean = s.replaceAll("[\\r\\n\\t]", " ");
                return clean.length() <= max ? clean : clean.substring(0, max);
        }
}
