package com.example.csvteamstats.batch;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {"app.cli.enabled=false", "app.batch.chunk-size=2"})
class TeamStatsBatchIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @TempDir static Path root;
    @Autowired ImportService imports;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource static void settings(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.csv.allowed-root", () -> root.toString());
    }

    @Test void deduplicatesReimportsPreservesCreatedAtAndStoresRejections() throws Exception {
        String match = "it-" + UUID.randomUUID();
        Path file = csv(match, row(match, "Points", "1") + row(match, "Points", "2")
                + row(match, "x".repeat(256), "3"));
        var first = imports.run(file.toString(), "first-" + match, "csv", -1, -1);
        assertThat(count(match)).isEqualTo(1);
        assertThat(value(match)).isEqualTo("2");
        var created = jdbc.queryForObject("SELECT created_at FROM team_stats WHERE match_id=?", Instant.class, match);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM team_stats_batch.outcome_events WHERE job_instance_id=? AND event_type='REJECTED'", Long.class,
                first.getJobInstance().getInstanceId())).isEqualTo(1);
        var repeat = imports.run(file.toString(), "first-" + match, "csv", -1, -1);
        assertThat(repeat.getId()).isEqualTo(first.getId());
        Files.writeString(file, header() + row(match, "Points", "9"));
        assertThatThrownBy(() -> imports.run(file.toString(), "first-" + match, "csv", -1, -1))
                .hasMessageContaining("different CSV contents");
        imports.run(file.toString(), "second-" + match, "csv", -1, -1);
        assertThat(count(match)).isEqualTo(1);
        assertThat(value(match)).isEqualTo("9");
        assertThat(jdbc.queryForObject("SELECT created_at FROM team_stats WHERE match_id=?", Instant.class, match)).isEqualTo(created);
        assertThat(jdbc.queryForObject("SELECT updated_at > created_at FROM team_stats WHERE match_id=?", Boolean.class, match)).isTrue();
    }

    @Test void reimportReplacesStaleRowsOfTheSamePeriodsAndKeepsOtherPeriods() throws Exception {
        String match = "replace-" + UUID.randomUUID();
        // Legacy import: shifted rows were accepted before validation existed, so seed them directly.
        for (String[] legacy : new String[][] {{"Q1", "Scoring", "(11/27)"}, {"Q1", "Scoring", "Total rebounds"},
                {"Q1", "Other", "Assists"}, {"Q9", "Scoring", "Only in old import"}}) {
            jdbc.update("""
                    INSERT INTO team_stats (match_id, period, category, metric, home_team, home_value,
                        away_team, away_value, source_event_id) VALUES (?, ?, ?, ?, 'Home', '1', 'Away', '1', 'legacy')
                    """, match, legacy[0], legacy[1], legacy[2]);
        }
        Path file = csv(match, match + ",Q1,Scoring,Field goal %,Home,40.7% (11/27),Away,50.0% (8/16),\n"
                + match + ",Q1,Rebounds,Total rebounds,Home,8,Away,13,\n"
                + match + ",Q1,Other,Assists,Home,8,Away,9,\n");
        imports.run(file.toString(), "fresh-" + match, "csv", -1, -1);
        assertThat(jdbc.queryForList("SELECT category || '/' || metric FROM team_stats WHERE match_id=? AND period='Q1' ORDER BY 1",
                String.class, match)).containsExactly("Other/Assists", "Rebounds/Total rebounds", "Scoring/Field goal %");
        assertThat(jdbc.queryForObject("SELECT home_value FROM team_stats WHERE match_id=? AND metric='Assists'", String.class, match))
                .isEqualTo("8");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM team_stats WHERE match_id=? AND period='Q9'", Long.class, match))
                .as("periods absent from the new CSV are kept").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM team_stats WHERE source_event_id='legacy' AND match_id<>?", Long.class, match))
                .as("other matches are untouched").isZero();
    }

    @Test void resumesAfterDatabaseFailureWithoutRewritingCommittedChunk() throws Exception {
        String match = "restart-" + UUID.randomUUID();
        Path file = csv(match, row(match, "A", "1") + row(match, "B", "2") + row(match, "FAIL", "3"));
        jdbc.execute("""
                CREATE FUNCTION public.test_batch_fail() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.metric = 'FAIL' THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$
                """);
        jdbc.execute("CREATE TRIGGER test_batch_failure BEFORE INSERT ON team_stats FOR EACH ROW EXECUTE FUNCTION public.test_batch_fail()");
        try {
            assertThatThrownBy(() -> imports.run(file.toString(), match, "csv", -1, -1))
                    .isInstanceOf(ImportService.ImportFailedException.class);
            assertThat(count(match)).isEqualTo(2);
        } finally {
            jdbc.execute("DROP TRIGGER test_batch_failure ON team_stats");
            jdbc.execute("DROP FUNCTION public.test_batch_fail()");
        }
        var checkpointTime = jdbc.queryForObject("SELECT updated_at FROM team_stats WHERE match_id=? AND metric='A'", Instant.class, match);
        imports.run(file.toString(), match, "csv", -1, -1);
        assertThat(count(match)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM team_stats WHERE match_id=? AND metric='A'", Instant.class, match)).isEqualTo(checkpointTime);
    }

    @Test void packagedCsvCommandExitsAndReportsFailureWithNonzeroStatus() throws Exception {
        String match = "cli-" + UUID.randomUUID();
        Path file = csv(match, row(match, "Points", "7"));
        assertThat(runJar(file)).isZero();
        assertThat(count(match)).isEqualTo(1);
        assertThat(runJar(root.resolve("missing.csv"))).isNotZero();
    }

    private int runJar(Path file) throws Exception {
        Path log = root.resolve("cli-" + UUID.randomUUID() + ".log");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", "target/csv-team-stats-batch.jar", "--file=" + file);
        builder.environment().put("DB_URL", postgres.getJdbcUrl());
        builder.environment().put("DB_USER", postgres.getUsername());
        builder.environment().put("DB_PASS", postgres.getPassword());
        builder.environment().put("CSV_ALLOWED_ROOT", root.toString());
        builder.environment().remove("SPRING_PROFILES_ACTIVE");
        var process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(45, java.util.concurrent.TimeUnit.SECONDS))
                    .as("CLI must exit; output: %s", Files.readString(log)).isTrue();
            return process.exitValue();
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    private long count(String match) { return jdbc.queryForObject("SELECT count(*) FROM team_stats WHERE match_id=?", Long.class, match); }
    private String value(String match) { return jdbc.queryForObject("SELECT home_value FROM team_stats WHERE match_id=?", String.class, match); }
    private static String header() { return String.join(",", CsvInput.HEADER) + "\n"; }
    private static String row(String match, String metric, String value) { return match + ",Q1,Shots," + metric + ",Home," + value + ",Away,,\n"; }
    private Path csv(String match, String rows) throws Exception {
        Path directory = Files.createDirectory(root.resolve(match));
        return Files.writeString(directory.resolve("stats_all_periods.csv"), header() + rows);
    }
}
