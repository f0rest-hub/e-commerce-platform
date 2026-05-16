package com.kev.ecom.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response object containing authentication details")
public class AuthResponse {
    @JsonProperty("access_token")
    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @JsonProperty("expires_in")
    @Schema(description = "Token expiration time in seconds", example = "3600")
    private long expiresIn;

    @JsonProperty("user_id")
    @Schema(description = "Unique ID of the authenticated user", example = "1")
    private Long userId;

    @Schema(description = "Email address of the user", example = "john.doe@example.com")
    private String email;

    @JsonProperty("full_name")
    @Schema(description = "Full name of the user", example = "John Doe")
    private String fullName;
}

