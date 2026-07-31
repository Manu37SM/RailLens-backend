package com.labs.train.train_db.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin scheduling trigger for {@link RefreshTokenService#purgeRevokedOrExpired()}
 * - kept separate from the service itself (business logic in services, the
 * "when" in a small dedicated component) rather than putting {@code
 * @Scheduled} directly on the service.
 *
 * Runs once a day; refresh tokens are long-lived (default 30 days) and this
 * is purely cleanup, not correctness-critical, so there's no need for
 * anything more frequent. Requires {@code @EnableScheduling} on {@code
 * TrainDbApplication} - nothing else in the app uses {@code @Scheduled} yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupTask {

        private final RefreshTokenService refreshTokenService;

        @Scheduled(cron = "0 0 3 * * *")
        public void purgeStaleRefreshTokens() {
                int deleted = refreshTokenService.purgeRevokedOrExpired();

                if (deleted > 0) {
                        log.info("Refresh token cleanup: removed {} revoked/expired row(s)", deleted);
                }
        }
}
