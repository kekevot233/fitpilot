package com.fitpilot.service;

import com.fitpilot.config.LoginProtectionProperties;
import com.fitpilot.exception.LoginProtectionUnavailableException;
import com.fitpilot.exception.LoginRateLimitedException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Profile("!no-db")
public class LoginRateLimiter {
    private static final DefaultRedisScript<Long> ACQUIRE = script("acquire");
    private static final DefaultRedisScript<Long> FINISH = script("finish");
    private final StringRedisTemplate redis;
    private final LoginProtectionProperties properties;

    public LoginRateLimiter(StringRedisTemplate redis, LoginProtectionProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public Permit acquire(String email, String source) {
        String digest = digest(email.trim().toLowerCase(Locale.ROOT) + "\0" + source);
        String prefix = "fitpilot:login:{" + digest + "}:";
        Permit permit = new Permit(prefix + "failures", prefix + "pending", UUID.randomUUID().toString());
        long retry = execute(ACQUIRE, permit, String.valueOf(properties.getMaxFailures()),
                String.valueOf(properties.getAttemptLeaseSeconds()), permit.id());
        if (retry > 0) throw new LoginRateLimitedException(retry);
        return permit;
    }

    public void failed(Permit permit) {
        long retry = finish(permit, "failure");
        if (retry > 0) throw new LoginRateLimitedException(retry);
    }

    public void succeeded(Permit permit) { finish(permit, "success"); }
    public void cancel(Permit permit) { finish(permit, "cancel"); }

    private long finish(Permit permit, String outcome) {
        return execute(FINISH, permit, outcome, String.valueOf(properties.getMaxFailures()),
                String.valueOf(properties.getWindowSeconds()), permit.id());
    }

    private long execute(DefaultRedisScript<Long> script, Permit permit, String... args) {
        try {
            Long result = redis.execute(script, List.of(permit.failuresKey(), permit.pendingKey()), (Object[]) args);
            if (result == null) throw new LoginProtectionUnavailableException();
            return result;
        } catch (DataAccessException exception) {
            // Do not expose Redis addresses, credentials or raw exceptions in API responses.
            throw new LoginProtectionUnavailableException();
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static DefaultRedisScript<Long> script(String name) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("redis/login-" + name + ".lua"));
        script.setResultType(Long.class);
        return script;
    }

    public record Permit(String failuresKey, String pendingKey, String id) { }
}
