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
import com.labs.train.train_db.exception.DuplicateUserException;
import com.labs.train.train_db.exception.InvalidCredentialsException;
import com.labs.train.train_db.model.AuthResponse;
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

        @InjectMocks
        private AuthService authService;

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
}
