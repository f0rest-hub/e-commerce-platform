package com.kev.ecom.service;

import com.kev.ecom.dto.auth.LoginRequest;
import com.kev.ecom.dto.auth.RegisterRequest;
import com.kev.ecom.model.auth.User;
import com.kev.ecom.repository.UserRepository;
import com.kev.ecom.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock UserRepository    userRepository;
    @Mock PasswordEncoder   passwordEncoder;
    @Mock JwtUtil           jwtUtil;

    @InjectMocks AuthService authService;

    private static final Long   USER_ID    = 1L;
    private static final String EMAIL      = "test@example.com";
    private static final String PASSWORD   = "password123";
    private static final String HASH       = "$2a$hash";
    private static final String TOKEN      = "jwt.token.here";
    private static final long   EXPIRES_IN = 86400L;

    private User savedUser() {
        return User.builder()
                .userId(USER_ID)
                .email(EMAIL)
                .passwordHash(HASH)
                .fullName("Test User")
                .createdAt(LocalDateTime.now())
                .build();
    }


    @Nested
    @DisplayName("register()")
    class Register {

        private RegisterRequest request() {
            RegisterRequest r = new RegisterRequest();
            r.setEmail(EMAIL);
            r.setPassword(PASSWORD);
            r.setFullName("Test User");
            return r;
        }

        @Test
        @DisplayName("returns AuthResponse when email is new")
        void successfulRegistration() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(Mono.just(false));
            when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(savedUser()));
            when(jwtUtil.generateToken(USER_ID, EMAIL)).thenReturn(TOKEN);
            when(jwtUtil.getExpirationSeconds()).thenReturn(EXPIRES_IN);

            StepVerifier.create(authService.register(request()))
                    .assertNext(response -> {
                        assertThat(response.getAccessToken()).isEqualTo(TOKEN);
                        assertThat(response.getUserId()).isEqualTo(USER_ID);
                        assertThat(response.getEmail()).isEqualTo(EMAIL);
                        assertThat(response.getFullName()).isEqualTo("Test User");
                        assertThat(response.getExpiresIn()).isEqualTo(EXPIRES_IN);
                    })
                    .verifyComplete();

            verify(passwordEncoder).encode(PASSWORD);
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("errors with IllegalArgumentException when email already exists")
        void duplicateEmail() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(Mono.just(true));

            StepVerifier.create(authService.register(request()))
                    .expectErrorMatches(ex ->
                            ex instanceof IllegalArgumentException &&
                                    ex.getMessage().contains(EMAIL))
                    .verify();

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("propagates repository error during existsByEmail")
        void repositoryErrorOnExistsCheck() {
            when(userRepository.existsByEmail(EMAIL))
                    .thenReturn(Mono.error(new RuntimeException("DB down")));

            StepVerifier.create(authService.register(request()))
                    .expectErrorMessage("DB down")
                    .verify();
        }

        @Test
        @DisplayName("propagates repository error during save")
        void repositoryErrorOnSave() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(Mono.just(false));
            when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
            when(userRepository.save(any(User.class)))
                    .thenReturn(Mono.error(new RuntimeException("DB down")));

            StepVerifier.create(authService.register(request()))
                    .expectErrorMessage("DB down")
                    .verify();
        }

        @Test
        @DisplayName("encodes password before saving")
        void passwordIsEncoded() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(Mono.just(false));
            when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(savedUser()));
            when(jwtUtil.generateToken(USER_ID, EMAIL)).thenReturn(TOKEN);
            when(jwtUtil.getExpirationSeconds()).thenReturn(EXPIRES_IN);

            StepVerifier.create(authService.register(request()))
                    .expectNextCount(1)
                    .verifyComplete();

            // Verify the saved user has the hashed password, not the raw one
            verify(userRepository).save(argThat(u -> HASH.equals(u.getPasswordHash())));
        }
    }

    @Nested
    @DisplayName("login()")
    class Login {

        private LoginRequest request() {
            LoginRequest r = new LoginRequest();
            r.setEmail(EMAIL);
            r.setPassword(PASSWORD);
            return r;
        }

        @Test
        @DisplayName("returns AuthResponse for valid credentials")
        void successfulLogin() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Mono.just(savedUser()));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
            when(jwtUtil.generateToken(USER_ID, EMAIL)).thenReturn(TOKEN);
            when(jwtUtil.getExpirationSeconds()).thenReturn(EXPIRES_IN);

            StepVerifier.create(authService.login(request()))
                    .assertNext(response -> {
                        assertThat(response.getAccessToken()).isEqualTo(TOKEN);
                        assertThat(response.getUserId()).isEqualTo(USER_ID);
                        assertThat(response.getEmail()).isEqualTo(EMAIL);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("errors when email not found")
        void emailNotFound() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Mono.empty());

            StepVerifier.create(authService.login(request()))
                    .expectErrorMatches(ex ->
                            ex instanceof IllegalArgumentException &&
                                    ex.getMessage().equals("Invalid email or password"))
                    .verify();

            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("errors when password does not match")
        void wrongPassword() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Mono.just(savedUser()));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

            StepVerifier.create(authService.login(request()))
                    .expectErrorMatches(ex ->
                            ex instanceof IllegalArgumentException &&
                                    ex.getMessage().equals("Invalid email or password"))
                    .verify();

            verify(jwtUtil, never()).generateToken(any(), any());
        }

        @Test
        @DisplayName("propagates repository error during findByEmail")
        void repositoryError() {
            when(userRepository.findByEmail(EMAIL))
                    .thenReturn(Mono.error(new RuntimeException("DB down")));

            StepVerifier.create(authService.login(request()))
                    .expectErrorMessage("DB down")
                    .verify();
        }
    }
}