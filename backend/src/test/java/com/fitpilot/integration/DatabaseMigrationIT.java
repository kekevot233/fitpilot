package com.fitpilot.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
}
