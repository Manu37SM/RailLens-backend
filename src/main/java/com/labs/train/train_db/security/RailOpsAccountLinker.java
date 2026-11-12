package com.labs.train.train_db.security;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.labs.train.train_db.entity.User;
import com.labs.train.train_db.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds the RailLens profile for a verified RailOps login (Phase 4B).
 *
 * <ul>
 *   <li>Linked by RailOps user id: that profile. This is the only way to reach an existing account; links are set
 *       when RailLens accounts are moved to RailOps (step 4C).</li>
 *   <li>Email not known to RailLens: a new profile is created (no RailLens password), linked to that id.</li>
 *   <li>Email already belongs to an unlinked RailLens account: refused. Neither app verifies email addresses, so
 *       merging here would let anyone who registered your email at RailOps open your RailLens account.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RailOpsAccountLinker {

        private static final SecureRandom RANDOM = new SecureRandom();

        public enum Outcome { LINKED, CREATED, EMAIL_CONFLICT }

        public record Result(Outcome outcome, User user) {
        }

        private final UserRepository users;

        public Result resolve(RailOpsTokenVerifier.Verified who) {
                Optional<User> linked = users.findByRailopsUserId(who.userId());
                if (linked.isPresent()) {
                        return new Result(Outcome.LINKED, linked.get());
                }
                if (users.existsByEmailIgnoreCase(who.email())) {
                        log.warn("RailOps login {} refused: its email belongs to an unlinked RailLens account", who.userId());
                        return new Result(Outcome.EMAIL_CONFLICT, null);
                }
                User profile = new User();
                profile.setUsername(freeUsername(who.email()));
                profile.setEmail(who.email().toLowerCase(Locale.ROOT));
                profile.setPasswordHash(null);
                profile.setRailopsUserId(who.userId());
                try {
                        User saved = users.save(profile);
                        log.info("Created RailLens profile {} for RailOps user {}", saved.getUsername(), who.userId());
                        return new Result(Outcome.CREATED, saved);
                } catch (DataIntegrityViolationException raced) {
                        // Two first requests at once: the other one created it.
                        return users.findByRailopsUserId(who.userId())
                                        .map(u -> new Result(Outcome.LINKED, u))
                                        .orElse(new Result(Outcome.EMAIL_CONFLICT, null));
                }
        }

        /** Readable and valid under RailLens' username rules (3-30 of [A-Za-z0-9_]), e.g. "asha_rao_3f9a1c". */
        String freeUsername(String email) {
                String local = email.substring(0, Math.max(0, email.indexOf('@'))).replaceAll("[^A-Za-z0-9_]", "_");
                if (local.length() < 3) {
                        local = "user";
                }
                local = local.substring(0, Math.min(local.length(), 22)).toLowerCase(Locale.ROOT);
                for (int i = 0; i < 5; i++) {
                        byte[] suffix = new byte[3];
                        RANDOM.nextBytes(suffix);
                        String candidate = local + "_" + HexFormat.of().formatHex(suffix);
                        if (!users.existsByUsername(candidate)) {
                                return candidate;
                        }
                }
                throw new IllegalStateException("could not find a free username");
        }
}
