package com.fitpilot.service;

import com.fitpilot.config.LoginProtectionProperties;
import com.fitpilot.exception.LoginProtectionUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginRateLimiterTest {
    @Test
    void missingRedisResultDoesNotBypassProtection() {
        var limiter = new LoginRateLimiter(mock(StringRedisTemplate.class), new LoginProtectionProperties());
        assertThatThrownBy(() -> limiter.acquire("test@example.com", "source"))
                .isInstanceOf(LoginProtectionUnavailableException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void connectionFailureUsesASafePublicError() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("private connection details"));
        var limiter = new LoginRateLimiter(redis, new LoginProtectionProperties());
        assertThatThrownBy(() -> limiter.acquire("test@example.com", "source"))
                .isInstanceOf(LoginProtectionUnavailableException.class)
                .hasMessage("登录服务暂时不可用，请稍后重试。");
    }
}
