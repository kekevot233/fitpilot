package com.fitpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.config.FoodCacheProperties;
import com.fitpilot.domain.Food;
import com.fitpilot.repository.FoodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BuiltInFoodSearchCacheTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final HashOperations<String, String, String> hash = mock(HashOperations.class);
    private final FoodRepository repository = mock(FoodRepository.class);
    private final FoodCacheProperties properties = new FoodCacheProperties();
    private final BuiltInFoodSearchCache cache = new BuiltInFoodSearchCache(
            redis, new ObjectMapper(), repository, properties);

    @BeforeEach
    void setup() {
        lenient().when(redis.<String, String>opsForHash()).thenReturn(hash);
        var food = new Food("熟米饭", new BigDecimal("130.00"), new BigDecimal("2.70"),
                new BigDecimal("28.20"), new BigDecimal("0.30"));
        ReflectionTestUtils.setField(food, "id", 1L);
        when(repository.findBuiltInByKeyword(anyString())).thenReturn(List.of(food));
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
}
