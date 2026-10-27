package com.fitpilot.integration;

import com.fitpilot.domain.MealType;
import com.fitpilot.dto.food.RecentFoodResponse;
import com.fitpilot.dto.food.CreateFoodRequest;
import com.fitpilot.dto.food.FoodResponse;
import com.fitpilot.service.BuiltInFoodSearchCache;
import com.fitpilot.service.FoodService;
import com.fitpilot.service.FoodLogService;
import com.fitpilot.service.WeightLogService;
import com.fitpilot.service.NutritionTrendService;
import com.fitpilot.repository.FoodLogRepository;
import com.fitpilot.dto.weight.UpsertWeightLogRequest;
import com.fitpilot.exception.ResourceNotFoundException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.time.LocalDate;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url", () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FoodService foodService;
    @Autowired
    private FoodLogService foodLogService;
    @Autowired
    private WeightLogService weightLogService;
    @Autowired
    private NutritionTrendService nutritionTrendService;
    @Autowired
    private FoodLogRepository foodLogRepository;
    @Autowired
    private StringRedisTemplate redis;

    @Test
    void upgradesAnExistingV3SchemaWithoutChangingFoodLogSnapshots() {
        String schema = "upgrade_v3_check";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("3").load().migrate();
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO upgrade_v3_check.users (activity_level, age, gender, goal, height_cm, nickname,
                                                   weekly_training_frequency, weight_kg)
                VALUES ('MODERATE', 22, 'MALE', 'MAINTAIN', 183, 'Upgrade fixture', 3, 76)
                RETURNING id
                """, Long.class);
        Long foodId = jdbcTemplate.queryForObject("""
                INSERT INTO upgrade_v3_check.foods (name, calories_per100g, protein_per100g, carbs_per100g, fat_per100g)
                VALUES ('Migration fixture', 130, 2.7, 28.2, 0.3) RETURNING id
                """, Long.class);
        jdbcTemplate.update("""
                INSERT INTO upgrade_v3_check.food_logs
                (user_id, food_id, grams, meal_type, eaten_at, food_name_snapshot, calories_per_100g_snapshot,
                 protein_per_100g_snapshot, carbs_per_100g_snapshot, fat_per_100g_snapshot)
                VALUES (?, ?, 200, 'LUNCH', '2026-10-19 12:00:00', 'Recorded snapshot', 123, 3, 25, 1)
                """, userId, foodId);
        String original = jdbcTemplate.queryForObject(
                "SELECT to_jsonb(log)::text FROM upgrade_v3_check.food_logs log", String.class);

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_jsonb(log)::text FROM upgrade_v3_check.food_logs log", String.class)).isEqualTo(original);
        assertThat(jdbcTemplate.queryForList("""
                SELECT version FROM upgrade_v3_check.flyway_schema_history
                WHERE type = 'SQL' AND success ORDER BY installed_rank
                """, String.class)).containsExactly("1", "2", "3", "4");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_indexes WHERE schemaname = 'upgrade_v3_check'
                  AND indexname IN ('idx_nutrition_targets_user_date_id', 'idx_food_logs_user_food_eaten_id')
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    @Transactional
    void sharedBuiltInCacheKeepsCustomFoodsPrivateAndReflectsUpdatesImmediately() {
        redis.delete(BuiltInFoodSearchCache.KEY);
        Long firstUser = insertWeightUser("cache-first@example.com");
        Long secondUser = insertWeightUser("cache-second@example.com");
        var request = new CreateFoodRequest("我的缓存测试米饭", new BigDecimal("150"),
                new BigDecimal("4"), new BigDecimal("25"), new BigDecimal("3"));
        var created = foodService.create(firstUser, request);

        assertThat(foodService.findAll(firstUser, " 米饭 ")).extracting(FoodResponse::name)
                .contains("熟米饭", request.name());
        assertThat(foodService.findAll(secondUser, "米饭")).extracting(FoodResponse::name)
                .contains("熟米饭").doesNotContain(request.name());
        var edited = new CreateFoodRequest("我的新缓存测试米饭", new BigDecimal("160"),
                request.proteinPer100g(), request.carbsPer100g(), request.fatPer100g());
        foodService.updateCustomFood(firstUser, created.id(), edited);
        assertThat(foodService.findAll(firstUser, "米饭")).extracting(FoodResponse::name)
                .contains(edited.name()).doesNotContain(request.name());
        foodService.archiveCustomFood(firstUser, created.id());
        assertThat(foodService.findAll(firstUser, "米饭")).extracting(FoodResponse::name)
                .doesNotContain(request.name(), edited.name());
        assertThat(foodService.findAll(secondUser, "%米饭")).extracting(FoodResponse::name)
                .contains("熟米饭").doesNotContain(request.name(), edited.name());
        assertThat(redis.opsForHash().size(BuiltInFoodSearchCache.KEY)).isPositive();
        assertThat(redis.opsForHash().values(BuiltInFoodSearchCache.KEY))
                .allSatisfy(json -> assertThat(json.toString()).doesNotContain("我的"));
    }

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

        assertThat(appliedVersions).containsExactly("1", "2", "3", "4");
        assertThat(businessTableCount).isEqualTo(9);
        assertThat(incompleteSnapshotColumns).isZero();
        assertThat(initializedFoods).isEqualTo(85);
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND indexname IN ('idx_nutrition_targets_user_date_id', 'idx_food_logs_user_food_eaten_id',
                                    'idx_food_logs_user_eaten_at', 'uk_weight_logs_user_date')
                """, String.class);
        assertThat(indexes).containsExactlyInAnyOrder("idx_nutrition_targets_user_date_id",
                "idx_food_logs_user_food_eaten_id", "idx_food_logs_user_eaten_at", "uk_weight_logs_user_date");
        assertThat(jdbcTemplate.queryForObject("SELECT pg_get_indexdef('idx_nutrition_targets_user_date_id'::regclass)", String.class))
                .contains("(user_id, effective_date DESC, id DESC)");
        assertThat(jdbcTemplate.queryForObject("SELECT pg_get_indexdef('idx_food_logs_user_food_eaten_id'::regclass)", String.class))
                .contains("(user_id, food_id, eaten_at DESC, id DESC)");
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

        var dashboard = foodLogService.getDailyDashboard(userId, LocalDate.of(2026, 10, 15));
        assertThat(dashboard.target()).isNull();
        assertThat(dashboard.remaining()).isNull();
        assertThat(dashboard.consumed().calories()).isEqualByComparingTo("364.0");
        assertThat(dashboard.foodLogs()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM nutrition_targets WHERE user_id = ?", Integer.class, userId)).isZero();
    }

    @Test
    @Transactional
    void deletesOnlyOwnWeightAndRecalculatesTheMovingAverage() {
        Long firstUser = insertWeightUser("weight-first@example.com");
        Long secondUser = insertWeightUser("weight-second@example.com");
        LocalDate endDate = LocalDate.of(2026, 10, 17);
        LocalDate earlierDate = endDate.minusDays(1);
        weightLogService.upsert(firstUser, earlierDate, new UpsertWeightLogRequest(new BigDecimal("80")));
        weightLogService.upsert(firstUser, endDate, new UpsertWeightLogRequest(new BigDecimal("78")));
        weightLogService.upsert(secondUser, earlierDate, new UpsertWeightLogRequest(new BigDecimal("90")));
        assertThat(weightLogService.getTrend(firstUser, endDate, 30).points().getLast().sevenDayAverageKg())
                .isEqualByComparingTo("79");

        weightLogService.delete(firstUser, earlierDate);

        var trend = weightLogService.getTrend(firstUser, endDate, 30);
        assertThat(trend.points()).hasSize(1);
        assertThat(trend.points().getFirst().sevenDayAverageKg()).isEqualByComparingTo("78");
        assertThat(trend.changeKg()).isNull();
        assertThatThrownBy(() -> weightLogService.delete(firstUser, earlierDate))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(weightLogService.getTrend(secondUser, endDate, 30).latestWeightKg()).isEqualByComparingTo("90");
    }

    @Test
    @Transactional
    void sqlTrendPreservesPerEntryRoundingSnapshotsAndDateAndUserBoundaries() {
        Long user = insertWeightUser("trend-sql@example.com");
        Long other = insertWeightUser("trend-other@example.com");
        Long food = insertFood("SQL trend fixture", "999", "99", "99", "99");
        // Each snapshot contributes 0.05 -> 0.1; rounding the final sum would incorrectly give 0.1.
        for (int i = 0; i < 2; i++) {
            insertFoodLog(user, food, "0.05", "SNACK", "2026-10-25 00:00:00", "Snapshot",
                    "100", "100", "100", "100");
        }
        insertFoodLog(user, food, "123.45", "LUNCH", "2026-10-27 23:59:59.999999", "Snapshot",
                "130.27", "2.69", "28.17", "0.28");
        insertFoodLog(user, food, "100", "LUNCH", "2026-10-24 23:59:59.999999", "Outside",
                "999", "99", "99", "99");
        insertFoodLog(user, food, "100", "LUNCH", "2026-10-28 00:00:00", "Outside",
                "999", "99", "99", "99");
        insertFoodLog(other, food, "100", "LUNCH", "2026-10-25 12:00:00", "Other user",
                "999", "99", "99", "99");
        jdbcTemplate.update("UPDATE foods SET active = false WHERE id = ?", food);
        LocalDate start = LocalDate.of(2026, 10, 25);
        LocalDate end = LocalDate.of(2026, 10, 27);
        var rows = foodLogRepository.summarizeNutritionByDate(user, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        assertThat(rows).hasSize(2);
        var trend = nutritionTrendService.getTrend(user, end, 3);
        assertThat(trend.points()).hasSize(3);
        assertThat(trend.daysLogged()).isEqualTo(2);
        assertThat(trend.points().getFirst().calories()).isEqualByComparingTo("0.2");
        assertThat(trend.points().getFirst().logCount()).isEqualTo(2);
        assertThat(trend.points().get(1).logCount()).isZero();
        for (var point : trend.points()) {
            var dashboard = foodLogService.getDailyDashboard(user, point.date());
            assertThat(point.calories()).isEqualByComparingTo(dashboard.consumed().calories());
            assertThat(point.protein()).isEqualByComparingTo(dashboard.consumed().protein());
            assertThat(point.carbs()).isEqualByComparingTo(dashboard.consumed().carbs());
            assertThat(point.fat()).isEqualByComparingTo(dashboard.consumed().fat());
            assertThat(point.targetCalories()).isZero();
        }
        assertThat(trend.averageCalories()).isEqualByComparingTo("80.5");
        assertThat(trend.averageTargetCalories()).isZero();
    }

    private Long insertWeightUser(String email) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (activity_level, age, gender, goal, height_cm, nickname,
                                   weekly_training_frequency, weight_kg, email, password_hash)
                VALUES ('MODERATE', 22, 'MALE', 'MAINTAIN', 183, 'Weight test', 3, 76, ?, 'test-hash')
                RETURNING id
                """, Long.class, email);
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
