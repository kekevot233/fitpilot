package com.fitpilot.service;

import com.fitpilot.domain.*;
import com.fitpilot.dto.auth.LoginRequest;
import com.fitpilot.exception.*;
import com.fitpilot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserProfileService profiles = mock(UserProfileService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final TokenService tokens = mock(TokenService.class);
    private final LoginRateLimiter limiter = mock(LoginRateLimiter.class);
    private final AuthService service = new AuthService(users, profiles, passwords, tokens, limiter);
    private final LoginRequest request = new LoginRequest("Test@Example.com", "password");
    private final LoginRateLimiter.Permit permit = new LoginRateLimiter.Permit("f", "p", "id");
    private final User user = new User("test@example.com", "encoded", "Test", Gender.MALE,
            22, 180.0, 75.0, ActivityLevel.MODERATE, Goal.MAINTAIN, 3);

    @BeforeEach
    void setup() {
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 1L);
        when(limiter.acquire("test@example.com", "source")).thenReturn(permit);
    }

    @Test
    void blockedRequestsNeverReadPasswordsOrIssueTokens() {
        when(limiter.acquire("test@example.com", "source")).thenThrow(new LoginRateLimitedException(30));
        assertThatThrownBy(() -> service.login(request, "source")).isInstanceOf(LoginRateLimitedException.class);
        verifyNoInteractions(users, passwords, tokens);
    }

    @Test
    void nonexistentUsersCountAsFailuresWithoutLeakingAccountExistence() {
        assertThatThrownBy(() -> service.login(request, "source")).isInstanceOf(InvalidCredentialsException.class);
        verify(limiter).failed(permit);
        verifyNoInteractions(tokens);
    }

    @Test
    void wrongPasswordCountsAsFailureAndCanReachTheLimit() {
        when(users.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        doThrow(new LoginRateLimitedException(900)).when(limiter).failed(permit);
        assertThatThrownBy(() -> service.login(request, "source")).isInstanceOf(LoginRateLimitedException.class);
        verifyNoInteractions(tokens);
    }

    @Test
    void successfulCredentialsClearFailuresBeforeIssuingTheToken() {
        when(users.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("password", "encoded")).thenReturn(true);
        when(tokens.issue(user)).thenReturn(new TokenService.IssuedToken("test-token", Instant.now().plusSeconds(60)));
        assertThat(service.login(request, "source").accessToken()).isEqualTo("test-token");
        var order = inOrder(limiter, tokens);
        order.verify(limiter).succeeded(permit);
        order.verify(tokens).issue(user);
    }

    @Test
    void databaseFailureReleasesTheReservationWithoutCountingABadPassword() {
        when(users.findByEmailIgnoreCase("test@example.com")).thenThrow(new IllegalStateException("DB failed"));
        assertThatThrownBy(() -> service.login(request, "source")).isInstanceOf(IllegalStateException.class);
        verify(limiter).cancel(permit);
        verify(limiter, never()).failed(any());
    }
}
