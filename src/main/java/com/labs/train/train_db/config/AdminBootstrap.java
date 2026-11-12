package com.labs.train.train_db.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.labs.train.train_db.entity.User;
import com.labs.train.train_db.repository.UserRepository;
import com.labs.train.train_db.security.MfaSecretCipher;

import lombok.extern.slf4j.Slf4j;

/**
 * Promotes the accounts named in {@code raillens.admin.bootstrap-usernames} (comma-separated, existing accounts only)
 * to ADMIN at startup. This is how the first admin is created without a database console; each promoted admin must
 * then set up MFA at their next sign-in.
 *
 * <p>Promotion only: removing a name from the list does NOT demote (that would be easy to do by accident). Demote with
 * {@code UPDATE users SET role='USER' WHERE username='...'}; their admin tokens expire within 15 minutes and refresh
 * re-reads the role.
 *
 * <p>Refuses to start if admins are configured but {@code raillens.mfa.encryption-key} is not: they could never sign in.
 */
@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

        private final UserRepository userRepository;
        private final MfaSecretCipher cipher;
        private final List<String> usernames;

        public AdminBootstrap(UserRepository userRepository, MfaSecretCipher cipher,
                        @Value("${raillens.admin.bootstrap-usernames:}") String usernames) {
                this.userRepository = userRepository;
                this.cipher = cipher;
                this.usernames = Arrays.stream(usernames.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }

        @Override
        public void run(ApplicationArguments args) {
                if (usernames.isEmpty()) {
                        return;
                }
                if (!cipher.isConfigured()) {
                        throw new IllegalStateException("raillens.admin.bootstrap-usernames is set but raillens.mfa.encryption-key "
                                        + "is not: admins could not sign in. Generate a key with `openssl rand -base64 32`.");
                }
                for (String username : usernames) {
                        userRepository.findByUsername(username).ifPresentOrElse(user -> {
                                if (!user.isAdmin()) {
                                        user.setRole(User.ROLE_ADMIN);
                                        userRepository.save(user);
                                        log.warn("Promoted {} to ADMIN (raillens.admin.bootstrap-usernames); MFA setup required at next sign-in",
                                                        username);
                                }
                        }, () -> log.warn("raillens.admin.bootstrap-usernames: no account named {} - register it first", username));
                }
        }
}
