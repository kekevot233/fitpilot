package com.fitpilot.controller;

import com.fitpilot.config.SecurityConfig;
import com.fitpilot.dto.auth.LoginRequest;
import com.fitpilot.exception.*;
import com.fitpilot.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AuthController.class, properties = {
        "fitpilot.security.jwt.secret=auth-test-secret-at-least-32-bytes",
        "fitpilot.security.jwt.issuer=fitpilot-test"
})
@Import({SecurityConfig.class, UserAccessGuard.class})
class AuthControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private LoginClientAddress address;
    private static final LoginRequest REQUEST = new LoginRequest("test@example.com", "wrong");

    @Test
    void returnsRetryAfterInHeaderAndBody() throws Exception {
        when(address.resolve(any())).thenReturn("198.51.100.1");
        when(auth.login(REQUEST, "198.51.100.1")).thenThrow(new LoginRateLimitedException(123));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "123"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(123));
    }

    @Test
    void redisFailureReturnsSafeRetryable503() throws Exception {
        when(auth.login(eq(REQUEST), any())).thenThrow(new LoginProtectionUnavailableException());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.detail").value("登录服务暂时不可用，请稍后重试。"));
    }

    @Test
    void invalidRequestNeverReachesTheLimiter() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auth);
    }
}
