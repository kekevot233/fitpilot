package com.fitpilot.integration;

import com.fitpilot.domain.MealType;
import com.fitpilot.dto.food.RecentFoodResponse;
import com.fitpilot.service.FoodService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.flyway.baseline-on-migrate=false",
                "spring.jpa.hibernate.ddl-auto=validate"
        }
)
class DatabaseMigrationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FoodService foodService;

    @Test
    void buildsAValidatedApplicationSchemaFromAnEmptyDatabase() {
        List<String> appliedVersions = jdbcTemplate.queryForList("""
                SELECT version
                FROM flyway_schema_history
                WHERE success = true AND type = 'SQL'
                ORDER BY installed_rank
                """, String.class);

        Integer businessTableCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name <> 'flyway_schema_history'
                """, Integer.class);

        Integer incompleteSnapshotColumns = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'food_logs'
                  AND column_name IN (
                    'food_name_snapshot',
                    'calories_per_100g_snapshot',
                    'protein_per_100g_snapshot',
                    'carbs_per_100g_snapshot',
                    'fat_per_100g_snapshot'
                  )
                  AND is_nullable <> 'NO'
                """, Integer.class);

        Integer initializedFoods = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM foods", Integer.class
        );

        assertThat(appliedVersions).containsExactly("1", "2", "3");
        assertThat(businessTableCount).isEqualTo(9);
        assertThat(incompleteSnapshotColumns).isZero();
        assertThat(initializedFoods).isEqualTo(85);
    }

    @Test
    @Transactional
    void returnsTheLatestPortionForEachRecentlyLoggedFood() {
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO users (
                    activity_level, age, gender, goal, height_cm, nickname,
                    weekly_training_frequency, weight_kg, email, password_hash
                ) VALUES ('MODERATE', 22, 'MALE', 'MAINTAIN', 183, 'Integration', 4, 76,
                          'recent-query@example.com', 'test-hash')
                RETURNING id
                """, Long.class);
        Long riceId = insertFood("集成测试米饭", "130", "2.7", "28.2", "0.3");
        Long milkId = insertFood("集成测试豆浆", "38", "3.0", "1.2", "1.6");

        insertFoodLog(userId, riceId, "150", "LUNCH", "2026-10-13 12:00:00", "集成测试米饭",
                "130", "2.7", "28.2", "0.3");
        insertFoodLog(userId, milkId, "300", "BREAKFAST", "2026-10-14 08:00:00", "集成测试豆浆",
                "38", "3.0", "1.2", "1.6");
        insertFoodLog(userId, riceId, "280", "DINNER", "2026-10-15 18:30:00", "集成测试米饭",
                "130", "2.7", "28.2", "0.3");

        List<RecentFoodResponse> recent = foodService.findRecentlyLoggedFoods(userId, 6);

        assertThat(recent).extracting(RecentFoodResponse::name)
                .containsExactly("集成测试米饭", "集成测试豆浆");
        assertThat(recent.getFirst().lastGrams()).isEqualByComparingTo("280");
        assertThat(recent.getFirst().lastMealType()).isEqualTo(MealType.DINNER);
    }

    private Long insertFood(String name, String calories, String protein, String carbs, String fat) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO foods (
                    name, calories_per100g, protein_per100g, carbs_per100g, fat_per100g, active
                ) VALUES (?, ?::numeric, ?::numeric, ?::numeric, ?::numeric, true)
                RETURNING id
                """, Long.class, name, calories, protein, carbs, fat);
    }

    private void insertFoodLog(Long userId, Long foodId, String grams, String mealType,
                               String eatenAt, String foodName, String calories,
                               String protein, String carbs, String fat) {
        jdbcTemplate.update("""
                INSERT INTO food_logs (
                    user_id, food_id, grams, meal_type, eaten_at, food_name_snapshot,
                    calories_per_100g_snapshot, protein_per_100g_snapshot,
                    carbs_per_100g_snapshot, fat_per_100g_snapshot
                ) VALUES (?, ?, ?::numeric, ?, ?::timestamp, ?, ?::numeric, ?::numeric,
                          ?::numeric, ?::numeric)
                """, userId, foodId, grams, mealType, eatenAt, foodName,
                calories, protein, carbs, fat);
    }
}
