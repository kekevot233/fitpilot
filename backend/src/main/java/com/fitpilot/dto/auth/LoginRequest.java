package com.fitpilot.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254)
        @Schema(example = "zhengyi@example.com")
        String email,

        @NotBlank @Size(max = 72)
        @Schema(example = "FitPilot123")
        String password
) {
}
