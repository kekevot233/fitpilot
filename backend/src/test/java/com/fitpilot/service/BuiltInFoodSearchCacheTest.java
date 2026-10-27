package com.fitpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.config.FoodCacheProperties;
import com.fitpilot.domain.Food;
import com.fitpilot.repository.FoodRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BuiltInFoodSearchCacheTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final HashOperations<String, String, String> hash = mock(HashOperations.class);
    private final FoodRepository repository = mock(FoodRepository.class);
    private final FoodCacheProperties properties = new FoodCacheProperties();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AtomicLong nanoTime = new AtomicLong();
    private final BuiltInFoodSearchCache cache = new BuiltInFoodSearchCache(
            redis, new ObjectMapper(), repository, properties, registry, nanoTime::get);

    @BeforeEach
    void setup() {
        lenient().when(redis.<String, String>opsForHash()).thenReturn(hash);
        var food = new Food("熟米饭", new BigDecimal("130.00"), new BigDecimal("2.70"),
                new BigDecimal("28.20"), new BigDecimal("0.30"));
        ReflectionTestUtils.setField(food, "id", 1L);
        when(repository.findBuiltInByKeyword(anyString())).thenReturn(List.of(food));
    }

    @Test
    void followersStopWaitingAndDoNotCancelTheOriginalDatabaseLoad() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.findBuiltInByKeyword("slow")).thenAnswer(call -> {
            if (entered.getCount() > 0) {
                entered.countDown();
                if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Release timed out");
            }
            return List.of();
        });
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var leader = worker.submit(() -> cache.search("slow"));
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(cache.search("slow")).isEmpty();
                assertThat(leader.isDone()).isFalse();
                assertThat(registry.get("fitpilot.food.cache.coalescing").tag("result", "timeout").counter().count()).isEqualTo(1);
            } finally {
                release.countDown();
            }
            assertThat(leader.get(2, TimeUnit.SECONDS)).isEmpty();
        }
        verify(repository, times(2)).findBuiltInByKeyword("slow");
    }

    @Test
    void databaseFailureReachesFollowersAndDoesNotPoisonLaterRequests() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var failure = new IllegalStateException("database unavailable");
        when(repository.findBuiltInByKeyword("failure")).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Release timed out");
            throw failure;
        }).thenReturn(List.of());
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var leader = workers.submit(() -> cache.search("failure"));
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                var follower = workers.submit(() -> cache.search("failure"));
                await().pollInterval(java.time.Duration.ofMillis(1)).atMost(java.time.Duration.ofSeconds(2))
                        .until(() -> registry.get("fitpilot.food.cache.coalescing").tag("result", "shared").counter().count() == 1);
                release.countDown();
                assertThatThrownBy(() -> follower.get(2, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class).hasCause(failure);
                assertThatThrownBy(() -> leader.get(2, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class).hasCause(failure);
            } finally {
                release.countDown();
            }
        }
        assertThat(cache.search("failure")).isEmpty();
        verify(repository, times(2)).findBuiltInByKeyword("failure");
    }

    @Test
    void excessDistinctRequestsBypassTheBoundedFlightTable() throws Exception {
        var entered = new CountDownLatch(64);
        var release = new CountDownLatch(1);
        when(repository.findBuiltInByKeyword(anyString())).thenAnswer(call -> {
            if (!call.getArgument(0).equals("overflow")) {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Release timed out");
            }
            return List.of();
        });
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var leaders = java.util.stream.IntStream.range(0, 64)
                    .mapToObj(i -> workers.submit(() -> cache.search("flight-" + i))).toList();
            try {
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(cache.search("overflow")).isEmpty();
                assertThat(registry.get("fitpilot.food.cache.coalescing").tag("result", "saturated").counter().count()).isEqualTo(1);
            } finally {
                release.countDown();
            }
            for (var leader : leaders) assertThat(leader.get(2, TimeUnit.SECONDS)).isEmpty();
        }
        assertThat(cache.search("overflow")).isEmpty();
        assertThat(registry.get("fitpilot.food.cache.coalescing").tag("result", "leader").counter().count()).isEqualTo(65);
    }

    @Test
    void unavailableRedisReadsFallBackWithoutTryingToWriteAgain() {
        when(hash.get(anyString(), anyString())).thenThrow(new RedisConnectionFailureException("private host"));
        assertThat(cache.search("米饭")).extracting(food -> food.name()).containsExactly("熟米饭");
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void failedWritesStillReturnDatabaseResults() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("private host"));
        assertThat(cache.search("米饭").getFirst().caloriesPer100g()).isEqualByComparingTo("130.00");
    }

    @Test
    void corruptJsonIsReplacedFromTheDatabase() {
        when(hash.get(anyString(), anyString())).thenReturn("broken json");
        assertThat(cache.search("米饭")).hasSize(1);
        verify(repository).findBuiltInByKeyword("米饭");
        verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void disabledCacheDoesNotAccessRedis() {
        properties.setEnabled(false);
        assertThat(cache.search("米饭")).hasSize(1);
        verifyNoInteractions(redis);
    }

    @Test
    void longKeywordsBypassRedisWithoutChangingTheDatabaseQuery() {
        String keyword = "a".repeat(129);
        assertThat(cache.search(keyword)).hasSize(1);
        verify(repository).findBuiltInByKeyword(keyword);
        verifyNoInteractions(redis);
    }

    @Test
    void failedReadBacksOffAndSuccessfulProbeRestoresTheCache() {
        when(hash.get(anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("private host"))
                .thenReturn(null);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        cache.search("米饭");
        cache.search("另一个关键词");
        verify(hash, times(1)).get(anyString(), anyString());
        assertThat(registry.get("fitpilot.food.cache.requests").tag("result", "read_error").counter().count()).isEqualTo(1);
        assertThat(registry.get("fitpilot.food.cache.requests").tag("result", "cooldown").counter().count()).isEqualTo(1);
        nanoTime.set(TimeUnit.SECONDS.toNanos(5));
        cache.search("米饭");
        cache.search("米饭");
        verify(hash, times(3)).get(anyString(), anyString());
        assertThat(registry.get("fitpilot.food.cache.writes").tag("result", "stored").counter().count()).isEqualTo(2);
        assertThat(registry.get("fitpilot.food.cache.database.load").timer().count()).isEqualTo(4);
    }

    @Test
    void writeFailureAlsoTriggersBackoffAndFullCacheIsNotCountedAsAFault() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("private host")).thenReturn(0L);
        cache.search("米饭");
        cache.search("另一个关键词");
        verify(hash, times(1)).get(anyString(), anyString());
        nanoTime.set(TimeUnit.SECONDS.toNanos(5));
        cache.search("米饭");
        cache.search("米饭");
        verify(hash, times(3)).get(anyString(), anyString());
        assertThat(registry.get("fitpilot.food.cache.writes").tag("result", "error").counter().count()).isEqualTo(1);
        assertThat(registry.get("fitpilot.food.cache.writes").tag("result", "capacity").counter().count()).isEqualTo(2);
    }

    @Test
    void onlyOneRequestProbesRedisWhileOtherRequestsContinueWithDatabaseResults() throws Exception {
        var probeEntered = new java.util.concurrent.CountDownLatch(1);
        var releaseProbe = new java.util.concurrent.CountDownLatch(1);
        when(hash.get(anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("private host"))
                .thenAnswer(call -> {
                    probeEntered.countDown();
                    if (!releaseProbe.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Probe release timed out");
                    return null;
                });
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        cache.search("米饭");
        nanoTime.set(TimeUnit.SECONDS.toNanos(5));
        try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var recovery = worker.submit(() -> cache.search("米饭"));
            try {
                assertThat(probeEntered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(cache.search("另一个关键词")).hasSize(1);
                verify(hash, times(2)).get(anyString(), anyString());
            } finally {
                releaseProbe.countDown();
            }
            assertThat(recovery.get(2, TimeUnit.SECONDS)).hasSize(1);
        }
    }
}
