package com.fitpilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.config.FoodCacheProperties;
import com.fitpilot.dto.food.FoodResponse;
import com.fitpilot.repository.FoodRepository;
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

    public BuiltInFoodSearchCache(StringRedisTemplate redis, ObjectMapper mapper,
                                 FoodRepository repository, FoodCacheProperties properties) {
        this.redis = redis;
        this.mapper = mapper;
        this.repository = repository;
        this.properties = properties;
    }

    public List<FoodResponse> search(String keyword) {
        // Preserve the database's case/collation and LIKE rules, including wildcard searches.
        boolean cacheable = properties.isEnabled() && keyword.length() <= 128;
        String field = cacheable ? digest(keyword) : "";
        if (cacheable) {
            try {
                String json = redis.<String, String>opsForHash().get(KEY, field);
                if (json != null) {
                    List<FoodResponse> cached = mapper.readValue(json, RESULTS);
                    if (cached != null && cached.stream().allMatch(food -> food != null
                            && food.id() > 0 && food.name() != null
                            && food.caloriesPer100g() != null && food.proteinPer100g() != null
                            && food.carbsPer100g() != null && food.fatPer100g() != null)) {
                        return List.copyOf(cached);
                    }
                }
            } catch (DataAccessException exception) {
                cacheable = false; // Avoid a second Redis timeout while serving this request.
                log.debug("Food cache unavailable; using database search");
            } catch (JsonProcessingException exception) {
                log.debug("Invalid food cache value; refreshing from database");
            }
        }

        List<FoodResponse> result = repository.findBuiltInByKeyword(keyword).stream()
                .map(food -> new FoodResponse(food.getId(), food.getName(), food.getCaloriesPer100g(),
                        food.getProteinPer100g(), food.getCarbsPer100g(), food.getFatPer100g()))
                .toList();
        if (cacheable) {
            try {
                String json = mapper.writeValueAsString(result);
                if (json.length() <= 262144) {
                    redis.execute(PUT, List.of(KEY), field, json,
                            String.valueOf(properties.getTtlSeconds()), "128");
                }
            } catch (DataAccessException | JsonProcessingException exception) {
                log.debug("Food cache write skipped; returning database results");
            }
        }
        return result;
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
