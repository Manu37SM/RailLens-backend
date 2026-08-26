package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.labs.train.train_db.entity.RefreshToken;
import com.labs.train.train_db.entity.User;
import com.labs.train.train_db.exception.InvalidCredentialsException;
import com.labs.train.train_db.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

        @Mock
        private RefreshTokenRepository refreshTokenRepository;

        private RefreshTokenService refreshTokenService;

        @BeforeEach
        void setUp() {
                refreshTokenService = new RefreshTokenService(refreshTokenRepository);
                ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationDays", 30L);
        }

        private static User user(long id) {
                User user = new User();
                user.setId(id);
                user.setUsername("manish");
                user.setEmail("manish@example.com");
                user.setPasswordHash("hashed");
                return user;
        }

        @Test
        void issueSavesAHashNotTheRawTokenAndReturnsTheRawToken() {

                User user = user(1L);

                String rawToken = refreshTokenService.issue(user);

                assertThat(rawToken).isNotBlank();

                ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
                verify(refreshTokenRepository).save(captor.capture());

                RefreshToken saved = captor.getValue();
                assertThat(saved.getUser()).isEqualTo(user);
                assertThat(saved.getTokenHash())
                                .isNotEqualTo(rawToken)
                                .hasSize(64);
                assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
        }

        @Test
        void issueProducesADifferentTokenEveryCall() {

                User user = user(1L);

                String first = refreshTokenService.issue(user);
                String second = refreshTokenService.issue(user);

                assertThat(first).isNotEqualTo(second);
        }

        @Test
        void rotateRevokesTheOldTokenAndIssuesANewOneForTheSameUser() {

                User user = user(1L);
                RefreshToken existing = new RefreshToken(user, "irrelevant-hash", LocalDateTime.now().plusDays(1));

                when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

                RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate("raw-token-value");

                assertThat(rotated.user()).isEqualTo(user);
                assertThat(rotated.rawToken()).isNotBlank();
                assertThat(existing.isRevoked()).isTrue();

                verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(any());
        }

        @Test
        void rotateRejectsAnUnknownToken() {

                when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

                assertThatThrownBy(() -> refreshTokenService.rotate("unknown-token"))
                                .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void rotateRejectsAnExpiredToken() {

                User user = user(1L);
                RefreshToken expired = new RefreshToken(user, "hash", LocalDateTime.now().minusDays(1));

                when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

                assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
                                .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void rotateRejectsAnAlreadyRevokedToken() {

                User user = user(1L);
                RefreshToken revoked = new RefreshToken(user, "hash", LocalDateTime.now().plusDays(1));
                revoked.setRevoked(true);

                when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

                assertThatThrownBy(() -> refreshTokenService.rotate("revoked-token"))
                                .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void revokeMarksAKnownTokenAsRevoked() {

                User user = user(1L);
                RefreshToken existing = new RefreshToken(user, "hash", LocalDateTime.now().plusDays(1));

                when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

                refreshTokenService.revoke("raw-token-value");

                assertThat(existing.isRevoked()).isTrue();
                verify(refreshTokenRepository).save(existing);
        }

        @Test
        void revokeIsANoOpForAnUnknownToken() {

                when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

                refreshTokenService.revoke("unknown-token");

                verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void revokeAllForUserDelegatesToTheRepository() {

                User user = user(1L);

                refreshTokenService.revokeAllForUser(user);

                verify(refreshTokenRepository).revokeAllForUser(user);
        }

        @Test
        void deleteAllForUserDelegatesToTheRepository() {

                User user = user(1L);

                refreshTokenService.deleteAllForUser(user);

                verify(refreshTokenRepository).deleteByUser(user);
        }

        @Test
        void purgeRevokedOrExpiredDelegatesToTheRepositoryAndReturnsTheDeletedCount() {

                when(refreshTokenRepository.deleteRevokedOrExpired(any())).thenReturn(7);

                int deleted = refreshTokenService.purgeRevokedOrExpired();

                assertThat(deleted).isEqualTo(7);
                verify(refreshTokenRepository).deleteRevokedOrExpired(any());
        }
}
