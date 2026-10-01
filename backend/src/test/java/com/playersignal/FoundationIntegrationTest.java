package com.playersignal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.password=test")
class FoundationIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine").withUsername("playersignal");
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.flywaydb.core.Flyway flyway;

    @Test void healthAndReadinessIncludeWorkingDatabase() {
        for (String path : new String[]{"/actuator/health", "/actuator/health/readiness"}) {
            var response = http.getForEntity(path, String.class);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).contains("\"status\":\"UP\"");
        }
    }

    @Test void reconnectWithUsernameMatchingSchemaDoesNotRepeatMigrations() {
        var restarted = org.flywaydb.core.Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .defaultSchema(flyway.getConfiguration().getDefaultSchema()).load();
        assertThat(restarted.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.flyway_schema_history WHERE success", Integer.class)).isEqualTo(flyway.info().applied().length);
    }

    @Test void flywayCreatesSchemaAndRecordsSuccessfulMigration() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'playersignal'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class)).isEqualTo(1);
    }
}
