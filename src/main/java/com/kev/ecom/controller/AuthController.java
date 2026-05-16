package com.kev.ecom.controller;

import com.kev.ecom.dto.auth.AuthResponse;
import com.kev.ecom.dto.auth.LoginRequest;
import com.kev.ecom.dto.auth.RegisterRequest;
import com.kev.ecom.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration and login")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user", description = "Creates a new user account and returns a JWT token")
    public Mono<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticates a user and returns a JWT token")
    public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}

