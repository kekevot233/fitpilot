package com.fitpilot.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitpilot.domain.Food;
import com.fitpilot.repository.FoodRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

@Component
@Profile("!no-db")
public class DefaultFoodDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultFoodDataInitializer.class);
    private static final String CATALOG_PATH = "data/default-foods.json";

    private final FoodRepository foodRepository;
    private final ObjectMapper objectMapper;

    public DefaultFoodDataInitializer(FoodRepository foodRepository, ObjectMapper objectMapper) {
        this.foodRepository = foodRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        renameLegacyFood("水煮全蛋", "水煮鸡蛋（全蛋）");
        List<DefaultFood> foods = readCatalog();
        foods.forEach(this::upsert);
        log.info("Initialized {} built-in foods from {}", foods.size(), CATALOG_PATH);
    }

    private void renameLegacyFood(String oldName, String newName) {
        if (foodRepository.existsByOwnerIsNullAndNameIgnoreCase(newName)) {
            return;
        }
        foodRepository.findByOwnerIsNullAndNameIgnoreCase(oldName).ifPresent(food -> {
            food.rename(newName);
            foodRepository.save(food);
        });
    }

    private List<DefaultFood> readCatalog() {
        ClassPathResource resource = new ClassPathResource(CATALOG_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read built-in food catalog.", exception);
        }
    }

    private void upsert(DefaultFood defaultFood) {
        Food food = foodRepository.findByOwnerIsNullAndNameIgnoreCase(defaultFood.name())
                .orElseGet(() -> new Food(
                        defaultFood.name(), defaultFood.calories(), defaultFood.protein(),
                        defaultFood.carbs(), defaultFood.fat()
                ));
        food.updateNutrition(
                defaultFood.calories(), defaultFood.protein(), defaultFood.carbs(), defaultFood.fat()
        );
        foodRepository.save(food);
    }

    private record DefaultFood(
            String name,
            BigDecimal calories,
            BigDecimal protein,
            BigDecimal carbs,
            BigDecimal fat
    ) {
    }
}
