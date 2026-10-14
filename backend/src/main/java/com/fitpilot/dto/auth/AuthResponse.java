package com.fitpilot.dto.auth;

import com.fitpilot.dto.user.UserProfileResponse;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserProfileResponse user
) {
}
