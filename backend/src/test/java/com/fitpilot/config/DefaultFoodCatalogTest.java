package com.fitpilot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFoodCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void catalogContainsValidUniqueFoods() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/data/default-foods.json")) {
            assertThat(inputStream).isNotNull();
            JsonNode foods = objectMapper.readTree(inputStream);
            Set<String> names = new HashSet<>();

            assertThat(foods.isArray()).isTrue();
            assertThat(foods.size()).isGreaterThanOrEqualTo(40);

            for (JsonNode food : foods) {
                assertThat(food.get("name").asText()).isNotBlank();
                assertThat(names.add(food.get("name").asText())).isTrue();
                assertThat(food.get("calories").asDouble()).isBetween(0.0, 1000.0);
                assertThat(food.get("protein").asDouble()).isBetween(0.0, 100.0);
                assertThat(food.get("carbs").asDouble()).isBetween(0.0, 100.0);
                assertThat(food.get("fat").asDouble()).isBetween(0.0, 100.0);
            }
        }
    }
}
