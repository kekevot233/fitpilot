package com.fitpilot.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.config.FoodCacheProperties;
import com.fitpilot.domain.Food;
import com.fitpilot.repository.FoodRepository;
import com.fitpilot.service.BuiltInFoodSearchCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Testcontainers
class FoodSearchCacheIT {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);
    private LettuceConnectionFactory connection;
    private StringRedisTemplate redis;
    private FoodRepository repository;
    private FoodCacheProperties properties;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setup() {
        connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
        redis.delete(BuiltInFoodSearchCache.KEY);
        repository = mock(FoodRepository.class);
        properties = new FoodCacheProperties();
        registry = new SimpleMeterRegistry();
    }

    @AfterEach
    void cleanup() { connection.destroy(); }

    private BuiltInFoodSearchCache instance() {
        return new BuiltInFoodSearchCache(redis, new ObjectMapper(), repository, properties, registry);
    }

    @Test
    void instancesShareJsonResultsAndCacheEmptySearches() {
        var food = new Food("熟米饭", new BigDecimal("130.00"), new BigDecimal("2.70"),
                new BigDecimal("28.20"), new BigDecimal("0.30"));
        ReflectionTestUtils.setField(food, "id", 1L);
        when(repository.findBuiltInByKeyword("米饭")).thenReturn(List.of(food));
        when(repository.findBuiltInByKeyword("不存在")).thenReturn(List.of());
        var first = instance();
        var second = instance();
        assertThat(second.search("米饭")).isEqualTo(first.search("米饭"));
        assertThat(second.search("米饭").getFirst().caloriesPer100g()).isEqualByComparingTo("130.00");
        assertThat(first.search("不存在")).isEmpty();
        assertThat(second.search("不存在")).isEmpty();
        verify(repository, times(1)).findBuiltInByKeyword("米饭");
        verify(repository, times(1)).findBuiltInByKeyword("不存在");
        assertThat(registry.get("fitpilot.food.cache.requests").tag("result", "hit").counter().count()).isEqualTo(3);
        assertThat(registry.get("fitpilot.food.cache.requests").tag("result", "miss").counter().count()).isEqualTo(2);
        assertThat(redis.opsForHash().keys(BuiltInFoodSearchCache.KEY))
                .allSatisfy(key -> assertThat(key.toString()).matches("[0-9a-f]{64}"));
        assertThat(redis.getExpire(BuiltInFoodSearchCache.KEY)).isBetween(1L, 300L);
    }

    @Test
    void cacheExpiresAndRequeriesTheDatabase() {
        properties.setTtlSeconds(1);
        when(repository.findBuiltInByKeyword("米饭")).thenReturn(List.of());
        var cache = instance();
        cache.search("米饭");
        await().atMost(Duration.ofSeconds(4)).until(() -> !Boolean.TRUE.equals(redis.hasKey(BuiltInFoodSearchCache.KEY)));
        cache.search("米饭");
        verify(repository, times(2)).findBuiltInByKeyword("米饭");
    }

    @Test
    void concurrentDistinctSearchesCannotExceedTheCapacityOrExtendTheWindow() throws Exception {
        when(repository.findBuiltInByKeyword(anyString())).thenReturn(List.of());
        var cache = instance();
        cache.search("first");
        redis.expire(BuiltInFoodSearchCache.KEY, Duration.ofSeconds(120));
        try (var executor = Executors.newFixedThreadPool(12)) {
            var tasks = java.util.stream.IntStream.range(0, 160)
                    .<Callable<Void>>mapToObj(index -> () -> { cache.search("keyword-" + index); return null; })
                    .toList();
            for (var future : executor.invokeAll(tasks)) future.get();
        }
        assertThat(redis.opsForHash().size(BuiltInFoodSearchCache.KEY)).isEqualTo(128);
        assertThat(redis.getExpire(BuiltInFoodSearchCache.KEY)).isBetween(1L, 120L);
        verify(repository, times(161)).findBuiltInByKeyword(anyString());
    }
}
