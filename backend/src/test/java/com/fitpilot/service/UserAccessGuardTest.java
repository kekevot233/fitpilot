package com.fitpilot.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccessGuardTest {

    private final UserAccessGuard guard = new UserAccessGuard();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsTheAuthenticatedUserToAccessTheirOwnData() {
        authenticateAs(7L);

        assertThatCode(() -> guard.check(7L)).doesNotThrowAnyException();
        assertThat(guard.currentUserId()).isEqualTo(7L);
    }

    @Test
    void rejectsAccessToAnotherUsersData() {
        authenticateAs(7L);

        assertThatThrownBy(() -> guard.check(8L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("another user's data");
    }

    @Test
    void rejectsRequestsWithoutAuthentication() {
        assertThatThrownBy(guard::currentUserId)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication is required");
    }

    private void authenticateAs(long userId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .claim("userId", userId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
