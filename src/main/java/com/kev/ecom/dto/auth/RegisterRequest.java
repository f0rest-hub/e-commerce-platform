package com.kev.ecom.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Locale;


@Data
@Schema(description = "Request object for user registration")
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @JsonProperty("full_name")
    @Schema(description = "Full name of the user", example = "John Doe")
    @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Full name must contain only alphabets with spaces, apostrophes, and hyphens.") //Added some more validations for allowing fancy names but not numbers or special characters.
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Email address of the user", example = "john.doe@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Schema(description = "Password for the new account", example = "password123")
    private String password;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
