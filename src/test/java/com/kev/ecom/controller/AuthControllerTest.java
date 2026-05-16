package com.kev.ecom.controller;

import com.kev.ecom.config.SecurityConfig;
import com.kev.ecom.dto.auth.AuthResponse;
import com.kev.ecom.dto.auth.LoginRequest;
import com.kev.ecom.dto.auth.RegisterRequest;
import com.kev.ecom.service.AuthService;
import com.kev.ecom.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Controller slice test — only the web layer is loaded.
 * AuthService is mocked; Spring Security auto-config is excluded via
 * @WebFluxTest so auth filters don't interfere.
 */
@WebFluxTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired
    WebTestClient webClient;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    JwtUtil jwtUtil;

    private static final String BASE = "/api/auth";

    private AuthResponse stubResponse() {
        return AuthResponse.builder()
                .accessToken("jwt.token")
                .expiresIn(86400L)
                .userId(1L)
                .email("test@example.com")
                .fullName("Test User")
                .build();
    }

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @Nested
    @DisplayName("POST /register")
    class Register {

        @Test
        @DisplayName("201 with AuthResponse on valid request")
        void success() {
            when(authService.register(any(RegisterRequest.class)))
                    .thenReturn(Mono.just(stubResponse()));

            webClient.post().uri(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"email":"test@example.com",
                             "password":"password123",
                             "full_name":"Test User"}
                            """)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(AuthResponse.class)
                    .value(r -> {
                        assertThat(r.getAccessToken()).isEqualTo("jwt.token");
                        assertThat(r.getUserId()).isEqualTo(1L);
                        assertThat(r.getEmail()).isEqualTo("test@example.com");
                    });
        }

        @Test
        @DisplayName("400 when email is blank")
        void blankEmail() {
            webClient.post().uri(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"email":"",
                             "password":"password123",
                             "full_name":"Test User"}
                            """)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("400 when password is too short")
        void shortPassword() {
            webClient.post().uri(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"email":"test@example.com",
                             "password":"short",
                             "full_name":"Test User"}
                            """)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("400 when email format is invalid")
        void invalidEmailFormat() {
            webClient.post().uri(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"email":"not-an-email",
                             "password":"password123",
                             "full_name":"Test User"}
                            """)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("400 when service throws IllegalArgumentException for duplicate email")
        void duplicateEmail() {
            when(authService.register(any(RegisterRequest.class)))
                    .thenReturn(Mono.error(new IllegalArgumentException("already exists")));

            webClient.post().uri(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"email":"test@example.com",
                             "password":"password123",
                             "full_name":"Test User"}
                            """)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("200 with AuthResponse on valid credentials")
        void success() {
            when(authService.login(any(LoginRequest.class)))
                    .thenReturn(Mono.just(stubResponse()));

            webClient.post().uri(BASE + "/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"email":"test@example.com",
                             "password":"password123"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(AuthResponse.class)
                    .value(r -> assertThat(r.getAccessToken()).isEqualTo("jwt.token"));
        }

        @Test
        @DisplayName("400 when service rejects credentials")
        void invalidCredentials() {
            when(authService.login(any(LoginRequest.class)))
                    .thenReturn(Mono.error(new IllegalArgumentException("Invalid email or password")));

            webClient.post().uri(BASE + "/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"email":"test@example.com",
                             "password":"wrongpass"}
                            """)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("400 when email is missing")
        void missingEmail() {
            webClient.post().uri(BASE + "/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"password":"password123"}
                            """)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }
}