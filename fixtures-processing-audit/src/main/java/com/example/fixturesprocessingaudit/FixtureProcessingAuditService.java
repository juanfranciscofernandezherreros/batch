package com.example.fixturesprocessingaudit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Audits every persisted fixture without changing raw CSV files or business data. */
@Service
public class FixtureProcessingAuditService {
    private static final Logger log = LoggerFactory.getLogger(FixtureProcessingAuditService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    // Audits every known match: the fixtures calendar plus results rows that never reached
    // fixtures (otherwise their missing datasets stay invisible). Fixtures data wins when a
    // match is in both tables.
    static final String AUDIT_SQL = """
            WITH matches AS (
                SELECT f.match_id, f.country, f.competition, f.event_datetime, f.home_team, f.away_team
                FROM fixtures.fixtures f
                UNION ALL
                SELECT r.match_id, r.country, r.competition, r.event_datetime, r.home_team, r.away_team
                FROM results.results r
                WHERE NOT EXISTS (SELECT 1 FROM fixtures.fixtures f WHERE f.match_id = r.match_id)
            )
            SELECT m.match_id, m.country, m.competition, m.event_datetime,
                   m.home_team, m.away_team,
                   EXISTS (SELECT 1 FROM results.results r WHERE r.match_id = m.match_id) AS results_present,
                   EXISTS (SELECT 1 FROM match_summary.match_summary s WHERE s.match_id = m.match_id) AS summary_present,
                   EXISTS (SELECT 1 FROM point_by_point.point_by_point_event p WHERE p.match_id = m.match_id) AS point_by_point_present,
                   EXISTS (SELECT 1 FROM team_stats.team_stats t WHERE t.match_id = m.match_id) AS team_stats_present,
                   EXISTS (SELECT 1 FROM stats_player.stats_player sp WHERE sp.match_id = m.match_id) AS player_stats_present,
                   EXISTS (SELECT 1 FROM fixtures_processing_audit.dataset_unavailable u WHERE u.match_id = m.match_id AND u.dataset = 'MATCH_SUMMARY') AS summary_unavailable,
                   EXISTS (SELECT 1 FROM fixtures_processing_audit.dataset_unavailable u WHERE u.match_id = m.match_id AND u.dataset = 'POINT_BY_POINT') AS point_by_point_unavailable,
                   EXISTS (SELECT 1 FROM fixtures_processing_audit.dataset_unavailable u WHERE u.match_id = m.match_id AND u.dataset = 'TEAM_STATS') AS team_stats_unavailable,
                   EXISTS (SELECT 1 FROM fixtures_processing_audit.dataset_unavailable u WHERE u.match_id = m.match_id AND u.dataset = 'PLAYER_STATS') AS player_stats_unavailable
            FROM matches m
            ORDER BY m.country, m.competition, m.event_datetime NULLS LAST, m.home_team, m.away_team
            """;

    // Registro persistente de datasets que la fuente (Flashscore) no publica para un
    // partido: tras varios intentos el CSV sale vacio. Sin este registro, la auditoria
    // marcaria el partido pendiente en cada arranque y el orquestador lo reabriria para
    // siempre. Se crea de forma idempotente porque este modulo es de solo lectura (no usa
    // Flyway); el esquema propio evita colisionar con las tablas de negocio.
    static final String ENSURE_REGISTRY_SQL = """
            CREATE SCHEMA IF NOT EXISTS fixtures_processing_audit;
            CREATE TABLE IF NOT EXISTS fixtures_processing_audit.dataset_unavailable (
                match_id   text        NOT NULL,
                dataset    text        NOT NULL,
                reason     text,
                attempts   integer     NOT NULL DEFAULT 1,
                first_seen timestamptz NOT NULL DEFAULT now(),
                last_seen  timestamptz NOT NULL DEFAULT now(),
                PRIMARY KEY (match_id, dataset),
                CONSTRAINT dataset_unavailable_dataset_check
                    CHECK (dataset IN ('MATCH_SUMMARY', 'POINT_BY_POINT', 'TEAM_STATS', 'PLAYER_STATS'))
            )
            """;

    // Alta o actualizacion del registro "sin datos en la fuente". Si el partido se vuelve a
    // intentar, suma un intento y refresca last_seen sin perder first_seen ni la razon inicial.
    static final String MARK_UNAVAILABLE_SQL = """
            INSERT INTO fixtures_processing_audit.dataset_unavailable (match_id, dataset, reason, attempts)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (match_id, dataset) DO UPDATE
               SET attempts = fixtures_processing_audit.dataset_unavailable.attempts + EXCLUDED.attempts,
                   reason   = COALESCE(EXCLUDED.reason, fixtures_processing_audit.dataset_unavailable.reason),
                   last_seen = now()
            """;

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final boolean runOnce;
    private final ZoneId zoneId;
    private final Path reportDirectory;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final Path leagueCatalog;
    private volatile AuditSnapshot latest = AuditSnapshot.notRun();

    @Autowired
    public FixtureProcessingAuditService(JdbcTemplate jdbc,
            @Value("${app.audit.enabled:true}") boolean enabled,
            @Value("${app.audit.run-once:false}") boolean runOnce,
            @Value("${app.audit.timezone:Europe/Madrid}") String timezone,
            @Value("${app.audit.report-directory:C:/data/basketball/reports/orchestrator}") Path reportDirectory,
            @Value("${app.audit.league-catalog:C:/Users/juanf/Desktop/BALONCESTO_AUTOMATIZADO/LIGAS_BASKET.json}") Path leagueCatalog,
            ObjectMapper objectMapper) {
        this(jdbc, enabled, runOnce, ZoneId.of(timezone), reportDirectory, Clock.system(ZoneId.of(timezone)), leagueCatalog, objectMapper);
    }

    FixtureProcessingAuditService(JdbcTemplate jdbc, boolean enabled, boolean runOnce, ZoneId zoneId, Path reportDirectory, Clock clock,
            Path leagueCatalog, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.runOnce = runOnce;
        this.zoneId = zoneId;
        this.reportDirectory = reportDirectory;
        this.clock = clock;
        this.leagueCatalog = leagueCatalog;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void auditOnStartup(ApplicationReadyEvent event) {
        ensureRegistry();
        if (enabled) runAudit();
        if (runOnce) event.getApplicationContext().close();
    }

    /** Crea el esquema y la tabla del registro "sin datos" si todavia no existen. */
    void ensureRegistry() {
        try {
            jdbc.execute(ENSURE_REGISTRY_SQL);
        } catch (RuntimeException exception) {
            log.error("event=fixtures_processing_audit_registry_setup_failed", exception);
        }
    }

    /**
     * Registra que la fuente no publica {@code dataset} para {@code matchId}: el CSV salio
     * vacio tras agotar los reintentos. A partir de aqui la auditoria deja de marcar ese
     * dataset como pendiente, de modo que el orquestador no lo reabra en cada arranque.
     */
    public void markDatasetUnavailable(String matchId, String dataset, String reason, int attempts) {
        String normalized = dataset == null ? "" : dataset.trim().toUpperCase(java.util.Locale.ROOT);
        if (!missingDatasetNames().contains(normalized)) {
            throw new IllegalArgumentException("Dataset no reconocido: " + dataset);
        }
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("match_id obligatorio");
        }
        jdbc.update(MARK_UNAVAILABLE_SQL, matchId, normalized, reason, Math.max(1, attempts));
        log.info("event=fixtures_processing_audit_dataset_unavailable match_id={} dataset={} reason={} attempts={}",
                matchId, normalized, reason, attempts);
        if (enabled && !runOnce) refreshAudit(false);
    }

    private static java.util.Set<String> missingDatasetNames() {
        return java.util.Set.of("MATCH_SUMMARY", "POINT_BY_POINT", "TEAM_STATS", "PLAYER_STATS");
    }

    public AuditSnapshot runAudit() {
        return refreshAudit(true);
    }

    @Scheduled(fixedDelayString = "${app.audit.refresh-interval-ms:5000}")
    public void refreshScheduledAudit() {
        if (enabled && !runOnce) refreshAudit(false);
    }

    private synchronized AuditSnapshot refreshAudit(boolean writeReports) {
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        try {
            List<FixtureRow> rows = jdbc.query(AUDIT_SQL, (rs, rowNumber) -> new FixtureRow(
                    rs.getString("match_id"), rs.getString("country"), rs.getString("competition"),
                    rs.getTimestamp("event_datetime") == null ? null : rs.getTimestamp("event_datetime").toInstant(),
                    rs.getString("home_team"), rs.getString("away_team"), rs.getBoolean("results_present"),
                    rs.getBoolean("summary_present"), rs.getBoolean("point_by_point_present"),
                    rs.getBoolean("team_stats_present"), rs.getBoolean("player_stats_present"),
                    rs.getBoolean("summary_unavailable"), rs.getBoolean("point_by_point_unavailable"),
                    rs.getBoolean("team_stats_unavailable"), rs.getBoolean("player_stats_unavailable")));
            List<AuditRow> auditRows = rows.stream().map(row -> toAuditRow(row, today, zoneId)).toList();
            latest = buildSnapshot(auditRows, today, writeReports);
            log.info("event=fixtures_processing_audit_completed fixtures={} eligible={} pending={} report={}",
                    latest.totalFixtures(), latest.eligibleFixtures(), latest.pendingFixtures(), latest.detailReport());
        } catch (RuntimeException | IOException exception) {
            latest = AuditSnapshot.failed(today, exception.getMessage());
            log.error("event=fixtures_processing_audit_failed", exception);
        }
        return latest;
    }

    public AuditSnapshot latest() {
        return latest;
    }

    static AuditRow toAuditRow(FixtureRow row, LocalDate today) {
        return toAuditRow(row, today, ZoneId.of("Europe/Madrid"));
    }

    static AuditRow toAuditRow(FixtureRow row, LocalDate today, ZoneId zoneId) {
        LocalDate matchDate = row.eventDatetime() == null ? null : row.eventDatetime().atZone(zoneId).toLocalDate();
        if (matchDate == null) return new AuditRow(row, null, "SIN_FECHA_AUDITABLE", List.of());
        if (!matchDate.isBefore(today)) return new AuditRow(row, matchDate, "HOY_O_FUTURO", List.of());
        List<String> missing = missingDatasets(row);
        return new AuditRow(row, matchDate, missing.isEmpty() ? "COMPLETO" : "PENDIENTE", missing);
    }

    static List<String> missingDatasets(FixtureRow row) {
        List<String> missing = new ArrayList<>();
        // RESULTS is the technical source record for a fixture, not an
        // actionable dataset. A missing source record is recovered by
        // processing the match summary, so expose one canonical action.
        // A dataset flagged as unavailable at the source is not actionable:
        // it stays out of the pending list so the orchestrator stops reopening it.
        if ((!row.resultsPresent() || !row.summaryPresent()) && !row.summaryUnavailable()) missing.add("MATCH_SUMMARY");
        if (!row.pointByPointPresent() && !row.pointByPointUnavailable()) missing.add("POINT_BY_POINT");
        if (!row.teamStatsPresent() && !row.teamStatsUnavailable()) missing.add("TEAM_STATS");
        if (!row.playerStatsPresent() && !row.playerStatsUnavailable()) missing.add("PLAYER_STATS");
        return List.copyOf(missing);
    }

    private AuditSnapshot buildSnapshot(List<AuditRow> rows, LocalDate today, boolean writeReports) throws IOException {
        String detailPath = latest.detailReport();
        String summaryPath = latest.summaryReport();
        Map<String, Counters> counters = new LinkedHashMap<>();
        for (AuditRow row : rows) counters.computeIfAbsent(row.fixture().country() + "/" + row.fixture().competition(), ignored -> new Counters()).add(row);
        for (LeagueCatalogEntry league : loadLeagueCatalog()) {
            counters.computeIfAbsent(league.country() + "/" + league.competition(), ignored -> new Counters());
        }
        if (writeReports) {
            Files.createDirectories(reportDirectory);
            String stamp = ZonedDateTime.now(clock.withZone(zoneId)).format(STAMP);
            Path detail = reportDirectory.resolve("FIXTURES_PROCESSING_AUDIT_" + stamp + ".csv");
            Path summary = reportDirectory.resolve("FIXTURES_PROCESSING_AUDIT_SUMMARY_" + stamp + ".csv");
            try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(detail), CSVFormat.DEFAULT.builder().setHeader(
                    "match_date", "country", "competition", "match_id", "home_team", "away_team", "status", "pending_datasets").build())) {
                for (AuditRow row : rows) {
                    printer.printRecord(row.matchDate(), row.fixture().country(), row.fixture().competition(), row.fixture().matchId(),
                            row.fixture().homeTeam(), row.fixture().awayTeam(), row.status(), String.join(",", row.pendingDatasets()));
                }
            }
            try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(summary), CSVFormat.DEFAULT.builder().setHeader(
                    "country", "competition", "coverage_status", "fixtures_total", "finalized_before_today", "complete", "pending", "missing_match_summary", "missing_point_by_point", "missing_team_stats", "missing_player_stats", "today_or_future", "without_auditable_date").build())) {
                for (Map.Entry<String, Counters> entry : counters.entrySet()) {
                    String[] identity = entry.getKey().split("/", 2);
                    Counters count = entry.getValue();
                    String coverage = count.total == 0 ? "SIN_FIXTURES_IMPORTADOS" : "AUDITABLE";
                    printer.printRecord(identity[0], identity[1], coverage, count.total, count.eligible, count.complete, count.pending,
                            count.missingSummary, count.missingPointByPoint, count.missingTeamStats, count.missingPlayerStats,
                            count.todayOrFuture, count.noDate);
                }
            }
            detailPath = detail.toString();
            summaryPath = summary.toString();
        }
        Counters total = new Counters();
        rows.forEach(total::add);
        List<PendingMatch> pendingMatches = rows.stream()
                .filter(row -> "PENDIENTE".equals(row.status()))
                .map(row -> new PendingMatch(row.matchDate(), row.fixture().country(), row.fixture().competition(), row.fixture().matchId(),
                        row.fixture().homeTeam(), row.fixture().awayTeam(), row.pendingDatasets()))
                .toList();
        String refreshedAt = ZonedDateTime.now(clock.withZone(zoneId)).toOffsetDateTime().toString();
        return new AuditSnapshot("COMPLETED", today, total.total, total.eligible, total.pending, detailPath, summaryPath,
                refreshedAt, pendingMatches, null);
    }

    private List<LeagueCatalogEntry> loadLeagueCatalog() {
        if (!Files.isRegularFile(leagueCatalog)) {
            log.warn("event=fixtures_processing_audit_catalog_missing path={}", leagueCatalog);
            return List.of();
        }
        try {
            return objectMapper.readValue(leagueCatalog.toFile(), new TypeReference<List<LeagueCatalogEntry>>() {});
        } catch (IOException exception) {
            log.warn("event=fixtures_processing_audit_catalog_unreadable path={}", leagueCatalog, exception);
            return List.of();
        }
    }

    record FixtureRow(String matchId, String country, String competition, java.time.Instant eventDatetime, String homeTeam,
            String awayTeam, boolean resultsPresent, boolean summaryPresent, boolean pointByPointPresent,
            boolean teamStatsPresent, boolean playerStatsPresent, boolean summaryUnavailable, boolean pointByPointUnavailable,
            boolean teamStatsUnavailable, boolean playerStatsUnavailable) {}

    record AuditRow(FixtureRow fixture, LocalDate matchDate, String status, List<String> pendingDatasets) {}

    record LeagueCatalogEntry(String code, String country, String competition, String resultsUrl) {}

    public record PendingMatch(LocalDate matchDate, String country, String competition, String matchId,
            String homeTeam, String awayTeam, List<String> pendingDatasets) {}

    public record AuditSnapshot(String status, LocalDate cutoffDate, int totalFixtures, int eligibleFixtures, int pendingFixtures,
            String detailReport, String summaryReport, String refreshedAt, List<PendingMatch> pendingMatches, String error) {
        static AuditSnapshot notRun() { return new AuditSnapshot("NOT_RUN", null, 0, 0, 0, null, null, null, List.of(), null); }
        static AuditSnapshot failed(LocalDate today, String error) { return new AuditSnapshot("FAILED", today, 0, 0, 0, null, null, null, List.of(), error); }
    }

    private static final class Counters {
        int total; int eligible; int complete; int pending; int missingSummary;
        int missingPointByPoint; int missingTeamStats; int missingPlayerStats; int todayOrFuture; int noDate;
        void add(AuditRow row) {
            total++;
            switch (row.status()) {
                case "COMPLETO" -> { eligible++; complete++; }
                case "PENDIENTE" -> {
                    eligible++; pending++;
                    if (row.pendingDatasets().contains("MATCH_SUMMARY")) missingSummary++;
                    if (row.pendingDatasets().contains("POINT_BY_POINT")) missingPointByPoint++;
                    if (row.pendingDatasets().contains("TEAM_STATS")) missingTeamStats++;
                    if (row.pendingDatasets().contains("PLAYER_STATS")) missingPlayerStats++;
                }
                case "HOY_O_FUTURO" -> todayOrFuture++;
                case "SIN_FECHA_AUDITABLE" -> noDate++;
                default -> throw new IllegalStateException("Unknown audit status: " + row.status());
            }
        }
    }
}
