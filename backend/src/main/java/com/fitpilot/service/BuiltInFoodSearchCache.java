package com.fitpilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.config.FoodCacheProperties;
import com.fitpilot.dto.food.FoodResponse;
import com.fitpilot.repository.FoodRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

@Service
@Profile("!no-db")
public class BuiltInFoodSearchCache {
    public static final String KEY = "fitpilot:foods:search:v1";
    private static final Logger log = LoggerFactory.getLogger(BuiltInFoodSearchCache.class);
    private static final TypeReference<List<FoodResponse>> RESULTS = new TypeReference<>() { };
    private static final DefaultRedisScript<Long> PUT = new DefaultRedisScript<>();
    static {
        PUT.setLocation(new ClassPathResource("redis/food-search-put.lua"));
        PUT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final FoodRepository repository;
    private final FoodCacheProperties properties;
    private enum Outcome { HIT, MISS, BYPASS, COOLDOWN, READ_ERROR, INVALID }
    private enum Write { STORED, CAPACITY, SKIPPED, ERROR }
    private record RedisState(long retryAtNanos, boolean probing) { }
    private static final RedisState READY = new RedisState(0, false);
    private final AtomicReference<RedisState> redisState = new AtomicReference<>(READY);
    private final LongSupplier nanoTime;
    private final Map<Outcome, Counter> requests = new EnumMap<>(Outcome.class);
    private final Map<Write, Counter> writes = new EnumMap<>(Write.class);
    private final Timer databaseLoads;
    private enum Coalescing { LEADER, SHARED, TIMEOUT, SATURATED, INTERRUPTED }
    private final Map<Coalescing, Counter> coalescing = new EnumMap<>(Coalescing.class);
    private final ConcurrentHashMap<String, CompletableFuture<List<FoodResponse>>> inFlight = new ConcurrentHashMap<>();
    private final Semaphore flightSlots = new Semaphore(64);

    @Autowired
    public BuiltInFoodSearchCache(StringRedisTemplate redis, ObjectMapper mapper,
                                 FoodRepository repository, FoodCacheProperties properties, MeterRegistry registry) {
        this(redis, mapper, repository, properties, registry, System::nanoTime);
    }

    BuiltInFoodSearchCache(StringRedisTemplate redis, ObjectMapper mapper,
                          FoodRepository repository, FoodCacheProperties properties,
                          MeterRegistry registry, LongSupplier nanoTime) {
        this.redis = redis;
        this.mapper = mapper;
        this.repository = repository;
        this.properties = properties;
        this.nanoTime = nanoTime;
        for (Outcome result : Outcome.values()) {
            requests.put(result, registry.counter("fitpilot.food.cache.requests", "result", result.name().toLowerCase(Locale.ROOT)));
        }
        for (Write result : Write.values()) {
            writes.put(result, registry.counter("fitpilot.food.cache.writes", "result", result.name().toLowerCase(Locale.ROOT)));
        }
        databaseLoads = registry.timer("fitpilot.food.cache.database.load");
        for (Coalescing result : Coalescing.values()) {
            coalescing.put(result, registry.counter("fitpilot.food.cache.coalescing", "result", result.name().toLowerCase(Locale.ROOT)));
        }
    }

    public List<FoodResponse> search(String keyword) {
        if (!properties.isEnabled() || keyword.length() > 128) return searchOnce(keyword);
        String field = digest(keyword);
        CompletableFuture<List<FoodResponse>> existing = inFlight.get(field);
        if (existing != null) return awaitResult(existing, keyword);
        if (!flightSlots.tryAcquire()) {
            coalescing.get(Coalescing.SATURATED).increment();
            return searchOnce(keyword);
        }
        var flight = new CompletableFuture<List<FoodResponse>>();
        existing = inFlight.putIfAbsent(field, flight);
        if (existing != null) {
            flightSlots.release();
            return awaitResult(existing, keyword);
        }
        coalescing.get(Coalescing.LEADER).increment();
        try {
            List<FoodResponse> result = searchOnce(keyword);
            flight.complete(result);
            return result;
        } catch (RuntimeException | Error exception) {
            flight.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(field, flight);
            flightSlots.release();
        }
    }

    private List<FoodResponse> awaitResult(CompletableFuture<List<FoodResponse>> flight, String keyword) {
        coalescing.get(Coalescing.SHARED).increment();
        try {
            // Bound waiting on another request; do not cancel its work when this request times out.
            return flight.get(200, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            coalescing.get(Coalescing.TIMEOUT).increment();
            return loadDatabase(keyword);
        } catch (InterruptedException exception) {
            coalescing.get(Coalescing.INTERRUPTED).increment();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Food search interrupted", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            if (exception.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("Food search failed", exception.getCause());
        }
    }

    private List<FoodResponse> searchOnce(String keyword) {
        // Preserve the database's case/collation and LIKE rules, including wildcard searches.
        boolean cacheable = properties.isEnabled() && keyword.length() <= 128;
        String field = cacheable ? digest(keyword) : "";
        Outcome outcome = cacheable ? Outcome.MISS : Outcome.BYPASS;
        RedisState probe = null;
        if (cacheable) {
            RedisState state = redisState.get();
            if (state != READY) {
                // Only one request probes Redis when the backoff expires; others keep using PostgreSQL.
                RedisState candidate = new RedisState(state.retryAtNanos(), true);
                if (state.probing() || nanoTime.getAsLong() - state.retryAtNanos() < 0
                        || !redisState.compareAndSet(state, candidate)) {
                    cacheable = false;
                    outcome = Outcome.COOLDOWN;
                } else {
                    probe = candidate;
                }
            }
        }
        if (cacheable) {
            try {
                String json = redis.<String, String>opsForHash().get(KEY, field);
                if (probe != null && redisState.compareAndSet(probe, READY)) {
                    log.info("Food search Redis cache recovered");
                }
                if (json != null) {
                    List<FoodResponse> cached = mapper.readValue(json, RESULTS);
                    if (cached != null && cached.stream().allMatch(food -> food != null
                            && food.id() > 0 && food.name() != null
                            && food.caloriesPer100g() != null && food.proteinPer100g() != null
                            && food.carbsPer100g() != null && food.fatPer100g() != null)) {
                        requests.get(Outcome.HIT).increment();
                        return List.copyOf(cached);
                    }
                    outcome = Outcome.INVALID;
                }
            } catch (DataAccessException exception) {
                cacheable = false; // Avoid a second Redis timeout while serving this request.
                outcome = Outcome.READ_ERROR;
                pauseCache();
            } catch (JsonProcessingException exception) {
                outcome = Outcome.INVALID;
                log.debug("Invalid food cache value; refreshing from database");
            } finally {
                // Never leave the single recovery probe occupied after an unexpected failure.
                if (probe != null) redisState.compareAndSet(probe, READY);
            }
        }

        requests.get(outcome).increment();
        List<FoodResponse> result = loadDatabase(keyword);
        if (cacheable && redisState.get() == READY) {
            try {
                String json = mapper.writeValueAsString(result);
                if (json.length() <= 262144) {
                    Long stored = redis.execute(PUT, List.of(KEY), field, json,
                            String.valueOf(properties.getTtlSeconds()), "128");
                    if (stored == null || (stored != 0 && stored != 1)) {
                        writes.get(Write.ERROR).increment();
                        pauseCache();
                    } else {
                        writes.get(stored == 1 ? Write.STORED : Write.CAPACITY).increment();
                    }
                } else {
                    writes.get(Write.SKIPPED).increment();
                }
            } catch (DataAccessException exception) {
                writes.get(Write.ERROR).increment();
                pauseCache();
            } catch (JsonProcessingException exception) {
                writes.get(Write.SKIPPED).increment();
                log.debug("Food cache write skipped; returning database results");
            }
        }
        return result;
    }

    private List<FoodResponse> loadDatabase(String keyword) {
        return databaseLoads.record(() -> repository.findBuiltInByKeyword(keyword).stream()
                .map(food -> new FoodResponse(food.getId(), food.getName(), food.getCaloriesPer100g(),
                        food.getProteinPer100g(), food.getCarbsPer100g(), food.getFatPer100g()))
                .toList());
    }

    private void pauseCache() {
        long retryAt = nanoTime.getAsLong() + TimeUnit.SECONDS.toNanos(properties.getFailureBackoffSeconds());
        RedisState previous = redisState.getAndSet(new RedisState(retryAt, false));
        if (previous == READY || previous.probing()) {
            // Deliberately omit the exception, connection URL, keyword and user data from logs.
            log.warn("Food search cache unavailable; temporarily using PostgreSQL");
        }
    }

    private static String digest(String keyword) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(keyword.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
