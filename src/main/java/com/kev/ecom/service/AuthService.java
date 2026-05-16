package com.kev.ecom.service;

import com.kev.ecom.dto.auth.AuthResponse;
import com.kev.ecom.dto.auth.LoginRequest;
import com.kev.ecom.dto.auth.RegisterRequest;
import com.kev.ecom.model.auth.User;
import com.kev.ecom.repository.UserRepository;
import com.kev.ecom.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public Mono<AuthResponse> register(RegisterRequest request) {
        return userRepository.existsByEmail(request.getEmail().toLowerCase())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("The Email you entered already exists: " + request.getEmail().toLowerCase()));
                    }
                    // TODO - Better to store passwords in key vault or similar. Even if hashed. Direct DB storage is poor...
                    User newUser = User.builder()
                            .email(request.getEmail())
                            .passwordHash(passwordEncoder.encode(request.getPassword()))
                            .fullName(request.getFullName())
                            .createdAt(LocalDateTime.now())
                            .build();

                    return userRepository.save(newUser);
                })
                .map(this::buildAuthResponse);
    }

    public Mono<AuthResponse> login(LoginRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid email or password")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                        return Mono.error(new IllegalArgumentException("Invalid email or password"));
                    }
                    return Mono.just(buildAuthResponse(user));
                });
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getUserId(), user.getEmail());
        return AuthResponse.builder()
                .accessToken(token)
                .expiresIn(jwtUtil.getExpirationSeconds())
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }
}