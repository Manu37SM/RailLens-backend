package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.labs.train.train_db.entity.User;
import com.labs.train.train_db.exception.AccountLockedException;
import com.labs.train.train_db.exception.DuplicateUserException;
import com.labs.train.train_db.exception.InvalidCredentialsException;
import com.labs.train.train_db.model.AuthResponse;
import com.labs.train.train_db.model.ChangePasswordRequest;
import com.labs.train.train_db.model.CurrentUserResponse;
import com.labs.train.train_db.model.LoginRequest;
import com.labs.train.train_db.model.RegisterRequest;
import com.labs.train.train_db.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

        @Mock
        private UserRepository userRepository;

        @Mock
        private PasswordEncoder passwordEncoder;

        @Mock
        private JwtService jwtService;

        @Mock
        private RefreshTokenService refreshTokenService;

        @InjectMocks
        private AuthService authService;

        @org.junit.jupiter.api.BeforeEach
        void configureLockoutThresholds() {
                org.springframework.test.util.ReflectionTestUtils.setField(
                                authService, "maxFailedLoginAttempts", 5);
                org.springframework.test.util.ReflectionTestUtils.setField(
                                authService, "lockoutDurationMinutes", 15L);
        }

        @Test
        void registerRejectsATakenUsernameBeforeTouchingTheDatabase() {
                RegisterRequest request = new RegisterRequest("manish", "manish@example.com", "password1");
                when(userRepository.existsByUsername("manish")).thenReturn(true);

                assertThatThrownBy(() -> authService.register(request))
                                .isInstanceOf(DuplicateUserException.class)
                                .hasMessageContaining("Username");

                verify(userRepository, org.mockito.Mockito.never()).save(any());
        }

        @Test
        void registerRejectsATakenEmail() {
                RegisterRequest request = new RegisterRequest("manish", "manish@example.com", "password1");
                when(userRepository.existsByUsername("manish")).thenReturn(false);
                when(userRepository.existsByEmail("manish@example.com")).thenReturn(true);

                assertThatThrownBy(() -> authService.register(request))
                                .isInstanceOf(DuplicateUserException.class)
                                .hasMessageContaining("Email");

                verify(userRepository, org.mockito.Mockito.never()).save(any());
        }

        @Test
        void registerHashesThePasswordAndReturnsAToken() {
                RegisterRequest request = new RegisterRequest("manish", "manish@example.com", "password1");
                when(userRepository.existsByUsername("manish")).thenReturn(false);
                when(userRepository.existsByEmail("manish@example.com")).thenReturn(false);
                when(passwordEncoder.encode("password1")).thenReturn("hashed-value");
                when(jwtService.generateToken("manish")).thenReturn("signed-jwt");
                when(jwtService.getExpirationSeconds()).thenReturn(3600L);
                when(refreshTokenService.issue(any())).thenReturn("refresh-token-value");

                AuthResponse response = authService.register(request);

                assertThat(response.token()).isEqualTo("signed-jwt");
                assertThat(response.username()).isEqualTo("manish");

                org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
                verify(userRepository).save(captor.capture());
                assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-value");
        }

        @Test
        void loginRejectsAnUnknownUsernameOrEmailWithAGenericMessage() {
                LoginRequest request = new LoginRequest("nobody", "password1");
                when(userRepository.findByUsernameOrEmail("nobody", "nobody")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> authService.login(request))
                                .isInstanceOf(InvalidCredentialsException.class)
                                .hasMessage("Invalid username/email or password");
        }

        @Test
        void loginRejectsAWrongPasswordWithTheSameGenericMessageAsAnUnknownUser() {
                User user = new User();
                user.setUsername("manish");
                user.setPasswordHash("hashed-value");

                LoginRequest request = new LoginRequest("manish", "wrong-password");
                when(userRepository.findByUsernameOrEmail("manish", "manish")).thenReturn(Optional.of(user));
                when(passwordEncoder.matches("wrong-password", "hashed-value")).thenReturn(false);

                assertThatThrownBy(() -> authService.login(request))
                                .isInstanceOf(InvalidCredentialsException.class)
                                .hasMessage("Invalid username/email or password");
        }

        @Test
        void loginReturnsATokenOnAMatchingPassword() {
                User user = new User();
                user.setUsername("manish");
                user.setEmail("manish@example.com");
                user.setPasswordHash("hashed-value");

                LoginRequest request = new LoginRequest("manish", "password1");
                when(userRepository.findByUsernameOrEmail("manish", "manish")).thenReturn(Optional.of(user));
                when(passwordEncoder.matches("password1", "hashed-value")).thenReturn(true);
                when(jwtService.generateToken("manish")).thenReturn("signed-jwt");
                when(jwtService.getExpirationSeconds()).thenReturn(3600L);
                when(refreshTokenService.issue(any())).thenReturn("refresh-token-value");

                AuthResponse response = authService.login(request);

                assertThat(response.token()).isEqualTo("signed-jwt");
                assertThat(response.email()).isEqualTo("manish@example.com");
        }

        @Test
        void getCurrentUserReturnsProfileDataForAKnownUsername() {
                User user = new User();
                user.setUsername("manish");
                user.setEmail("manish@example.com");
                user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                when(userRepository.findByUsername("manish")).thenReturn(Optional.of(user));

                CurrentUserResponse response = authService.getCurrentUser("manish");

                assertThat(response.username()).isEqualTo("manish");
                assertThat(response.email()).isEqualTo("manish@example.com");
        }

        @Test
        void refreshDelegatesToRefreshTokenServiceAndReturnsANewAccessToken() {
                User user = new User();
                user.setUsername("manish");
                user.setEmail("manish@example.com");

                when(refreshTokenService.rotate("old-refresh-token"))
                                .thenReturn(new RefreshTokenService.RotatedToken(user, "new-refresh-token"));
                when(jwtService.generateToken("manish")).thenReturn("new-access-token");
                when(jwtService.getExpirationSeconds()).thenReturn(3600L);

                AuthResponse response = authService.refresh("old-refresh-token");

                assertThat(response.token()).isEqualTo("new-access-token");
                assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
                assertThat(response.username()).isEqualTo("manish");
        }

        @Test
        void refreshPropagatesInvalidCredentialsExceptionForABadRefreshToken() {
                when(refreshTokenService.rotate("bad-token"))
                                .thenThrow(new InvalidCredentialsException("Invalid or expired refresh token"));

                assertThatThrownBy(() -> authService.refresh("bad-token"))
                                .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void logoutDelegatesToRefreshTokenServiceRevoke() {
                authService.logout("some-refresh-token");

                verify(refreshTokenService).revoke("some-refresh-token");
        }

        @Test
        void changePasswordRevokesEveryOutstandingRefreshTokenForTheUser() {
                User user = new User();
                user.setUsername("manish");
                user.setPasswordHash("old-hash");

                ChangePasswordRequest request = new ChangePasswordRequest("old-password", "newPassword1");
                when(userRepository.findByUsername("manish")).thenReturn(Optional.of(user));
                when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
                when(passwordEncoder.encode("newPassword1")).thenReturn("new-hash");

                authService.changePassword("manish", request);

                assertThat(user.getPasswordHash()).isEqualTo("new-hash");
                verify(refreshTokenService).revokeAllForUser(user);
        }

        @Test
        void loginLocksTheAccountAfterTheConfiguredNumberOfFailedAttempts() {
                User user = new User();
                user.setUsername("manish");
                user.setPasswordHash("hashed-value");
                user.setFailedLoginAttempts(4);

                LoginRequest request = new LoginRequest("manish", "wrong-password");
                when(userRepository.findByUsernameOrEmail("manish", "manish")).thenReturn(Optional.of(user));
                when(passwordEncoder.matches("wrong-password", "hashed-value")).thenReturn(false);

                assertThatThrownBy(() -> authService.login(request))
                                .isInstanceOf(InvalidCredentialsException.class);

                assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
                assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now());
        }

        @Test
        void loginRejectsACorrectPasswordWhileTheAccountIsLocked() {
                User user = new User();
                user.setUsername("manish");
                user.setPasswordHash("hashed-value");
                user.setFailedLoginAttempts(5);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(10));

                LoginRequest request = new LoginRequest("manish", "password1");
                when(userRepository.findByUsernameOrEmail("manish", "manish")).thenReturn(Optional.of(user));

                assertThatThrownBy(() -> authService.login(request))
                                .isInstanceOf(AccountLockedException.class);

                verify(passwordEncoder, org.mockito.Mockito.never()).matches(any(), any());
        }

        @Test
        void loginAutoUnlocksAndSucceedsOnceTheLockoutWindowHasPassed() {
                User user = new User();
                user.setUsername("manish");
                user.setEmail("manish@example.com");
                user.setPasswordHash("hashed-value");
                user.setFailedLoginAttempts(5);
                user.setLockedUntil(LocalDateTime.now().minusSeconds(1));

                LoginRequest request = new LoginRequest("manish", "password1");
                when(userRepository.findByUsernameOrEmail("manish", "manish")).thenReturn(Optional.of(user));
                when(passwordEncoder.matches("password1", "hashed-value")).thenReturn(true);
                when(jwtService.generateToken("manish")).thenReturn("signed-jwt");
                when(jwtService.getExpirationSeconds()).thenReturn(3600L);
                when(refreshTokenService.issue(any())).thenReturn("refresh-token-value");

                AuthResponse response = authService.login(request);

                assertThat(response.token()).isEqualTo("signed-jwt");
                assertThat(user.getLockedUntil()).isNull();
                assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
        }

        @Test
        void loginResetsTheFailedAttemptCounterOnSuccess() {
                User user = new User();
                user.setUsername("manish");
                user.setEmail("manish@example.com");
                user.setPasswordHash("hashed-value");
                user.setFailedLoginAttempts(3);

                LoginRequest request = new LoginRequest("manish", "password1");
                when(userRepository.findByUsernameOrEmail("manish", "manish")).thenReturn(Optional.of(user));
                when(passwordEncoder.matches("password1", "hashed-value")).thenReturn(true);
                when(jwtService.generateToken("manish")).thenReturn("signed-jwt");
                when(jwtService.getExpirationSeconds()).thenReturn(3600L);
                when(refreshTokenService.issue(any())).thenReturn("refresh-token-value");

                authService.login(request);

                assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
        }
}
