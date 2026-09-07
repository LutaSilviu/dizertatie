package ro.sluta.accessibility.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ro.sluta.accessibility.domain.AnalysisStatus;
import ro.sluta.accessibility.experiment.ExperimentFreezeRequest;
import ro.sluta.accessibility.experiment.ExperimentMode;
import ro.sluta.accessibility.experiment.ExperimentRunnerService;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresqlFlywayContainerIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
    @Autowired JdbcClient jdbc;
    @Autowired ResearchPersistenceService persistence;
    @Autowired ExperimentRunnerService experiments;

    @Test void flywayBuildsPostgresqlFromZeroAndRoundTripsAnAnalysis() {
        Integer migrations = jdbc.sql("select count(*) from flyway_schema_history where success=true").query(Integer.class).single();
        assertThat(migrations).isEqualTo(2);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS); UUID id = UUID.randomUUID();
        AnalysisRecord value = new AnalysisRecord(id, null, "https://example.com", "{}", AnalysisStatus.CREATED, 0, now, now, 0);
        persistence.saveAnalysis(value);
        assertThat(persistence.findAnalysis(id)).contains(value);
        var pilot = experiments.createPlan(new ExperimentFreezeRequest(20260903L, 1,
                new BigDecimal("2.00"), new BigDecimal("0.01"), List.of("S01", "S04")),
                ExperimentMode.PILOT, false);
        assertThat(pilot.plannedCases()).isEqualTo(8);
        assertThat(pilot.executions()).hasSize(152);
        assertThat(jdbc.sql("select count(*) from ground_truth_issue").query(Integer.class).single()).isEqualTo(48);
    }
}
