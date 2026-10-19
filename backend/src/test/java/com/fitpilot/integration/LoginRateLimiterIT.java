package com.fitpilot.integration;

import com.fitpilot.config.LoginProtectionProperties;
import com.fitpilot.exception.LoginRateLimitedException;
import com.fitpilot.service.LoginRateLimiter;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
class LoginRateLimiterIT {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);
    private static final List<LettuceConnectionFactory> connections = new ArrayList<>();
    private LoginProtectionProperties properties;
    private LoginRateLimiter first;
    private LoginRateLimiter second;
    private String email;

    @BeforeEach
    void setup() {
        properties = new LoginProtectionProperties();
        first = instance();
        second = instance();
        email = UUID.randomUUID() + "@example.com";
    }

    @AfterEach
    void cleanup() { connections.forEach(LettuceConnectionFactory::destroy); connections.clear(); }

    private LoginRateLimiter instance() {
        var factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        connections.add(factory);
        return new LoginRateLimiter(new StringRedisTemplate(factory), properties);
    }

    @Test
    void instancesShareFiveFailuresButOtherAccountsAndSourcesAreIndependent() {
        for (int i = 0; i < 4; i++) first.failed(first.acquire(email, "source"));
        assertThatThrownBy(() -> second.failed(second.acquire(email, "source")))
                .isInstanceOf(LoginRateLimitedException.class);
        assertThatThrownBy(() -> first.acquire(email.toUpperCase(Locale.ROOT), "source"))
                .isInstanceOf(LoginRateLimitedException.class);
        second.cancel(second.acquire(email, "different-source"));
        second.cancel(second.acquire("other-" + email, "source"));
    }

    @Test
    void successClearsFailuresWithoutClearingOtherPendingRequests() {
        for (int i = 0; i < 3; i++) first.failed(first.acquire(email, "source"));
        var ongoing = first.acquire(email, "source");
        second.succeeded(second.acquire(email, "source"));
        List<LoginRateLimiter.Permit> fresh = new ArrayList<>();
        for (int i = 0; i < 4; i++) fresh.add(first.acquire(email, "source"));
        assertThatThrownBy(() -> first.acquire(email, "source")).isInstanceOf(LoginRateLimitedException.class);
        first.cancel(ongoing);
        fresh.forEach(first::cancel);
        for (int i = 0; i < 4; i++) first.failed(first.acquire(email, "source"));
        second.cancel(second.acquire(email, "source"));
    }

    @Test
    void expiresTheFailureWindowAndProvidesAPositiveRetryTime() {
        properties.setMaxFailures(1);
        properties.setWindowSeconds(1);
        var permit = first.acquire(email, "source");
        assertThatThrownBy(() -> first.failed(permit)).isInstanceOfSatisfying(LoginRateLimitedException.class,
                exception -> assertThat(exception.getRetryAfterSeconds()).isPositive());
        await().atMost(Duration.ofSeconds(4)).ignoreException(LoginRateLimitedException.class)
                .untilAsserted(() -> second.cancel(second.acquire(email, "source")));
    }

    @Test
    void concurrentRequestsCannotAllPassBeforeFailuresAreRecorded() throws Exception {
        var executor = Executors.newFixedThreadPool(20);
        var start = new CountDownLatch(1);
        try {
            List<Future<Optional<LoginRateLimiter.Permit>>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                LoginRateLimiter instance = i % 2 == 0 ? first : second;
                futures.add(executor.submit(() -> {
                    start.await();
                    try { return Optional.of(instance.acquire(email, "source")); }
                    catch (LoginRateLimitedException exception) { return Optional.empty(); }
                }));
            }
            start.countDown();
            List<LoginRateLimiter.Permit> admitted = new ArrayList<>();
            for (var future : futures) future.get(10, TimeUnit.SECONDS).ifPresent(admitted::add);
            assertThat(admitted).hasSize(5);
            for (var permit : admitted) first.cancel(permit);
            first.cancel(first.acquire(email, "source"));
            assertThat(admitted.getFirst().failuresKey()).doesNotContain(email, "source");
        } finally { executor.shutdownNow(); }
    }

    @Test
    void abandonedReservationsExpireAndCannotCancelANewerReservation() {
        properties.setMaxFailures(1);
        properties.setAttemptLeaseSeconds(1);
        var abandoned = first.acquire(email, "source");
        assertThatThrownBy(() -> second.acquire(email, "source")).isInstanceOf(LoginRateLimitedException.class);
        await().atMost(Duration.ofSeconds(4)).ignoreException(LoginRateLimitedException.class)
                .untilAsserted(() -> {
                    var fresh = second.acquire(email, "source");
                    first.cancel(abandoned);
                    assertThatThrownBy(() -> first.acquire(email, "source")).isInstanceOf(LoginRateLimitedException.class);
                    second.cancel(fresh);
                });
    }
}
