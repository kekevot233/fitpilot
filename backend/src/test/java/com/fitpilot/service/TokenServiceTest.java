package com.fitpilot.service;

import com.fitpilot.config.SecurityConfig;
import com.fitpilot.domain.ActivityLevel;
import com.fitpilot.domain.Gender;
import com.fitpilot.domain.Goal;
import com.fitpilot.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;

import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    @Test
    void issuesAValidSignedTokenContainingTheUserIdentity() {
        SecurityConfig securityConfig = new SecurityConfig();
        SecretKey key = securityConfig.jwtSecretKey(
                "fitpilot-test-secret-with-more-than-thirty-two-bytes"
        );
        JwtEncoder encoder = securityConfig.jwtEncoder(key);
        JwtDecoder decoder = securityConfig.jwtDecoder(key, "https://fitpilot.test");
        TokenService tokenService = new TokenService(encoder, "https://fitpilot.test", 60);

        User user = new User(
                "runner@example.com", "unused-hash", "Runner", Gender.FEMALE, 24,
                168.0, 58.0, ActivityLevel.MODERATE, Goal.MAINTAIN, 3
        );
        ReflectionTestUtils.setField(user, "id", 42L);

        TokenService.IssuedToken issued = tokenService.issue(user);
        Jwt decoded = decoder.decode(issued.value());

        assertThat(decoded.getIssuer().toString()).isEqualTo("https://fitpilot.test");
        assertThat(decoded.getSubject()).isEqualTo("runner@example.com");
        assertThat(decoded.<Long>getClaim("userId")).isEqualTo(42L);
        assertThat(decoded.getExpiresAt())
                .isEqualTo(issued.expiresAt().truncatedTo(ChronoUnit.SECONDS));
    }
}
