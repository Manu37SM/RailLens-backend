package com.labs.train.train_db.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
