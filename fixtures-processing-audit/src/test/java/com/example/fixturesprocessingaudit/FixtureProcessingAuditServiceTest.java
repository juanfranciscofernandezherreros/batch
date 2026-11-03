package com.example.fixturesprocessingaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixtureProcessingAuditServiceTest {
    @Test void marksOnlyDatasetsMissingForAPastFixture() {
        var fixture = new FixtureProcessingAuditService.FixtureRow("m1", "spain", "liga-endesa",
                Instant.parse("2026-10-30T19:00:00Z"), "Local", "Visitante", true, false, true, false, false,
                false, false, false, false);

        var audit = FixtureProcessingAuditService.toAuditRow(fixture, LocalDate.of(2026, 11, 1));

        assertEquals("PENDIENTE", audit.status());
        assertEquals(List.of("MATCH_SUMMARY", "TEAM_STATS", "PLAYER_STATS"), audit.pendingDatasets());
    }

    @Test void excludesDatasetsFlaggedUnavailableAtTheSource() {
        // La fuente no publica POINT_BY_POINT ni PLAYER_STATS: aunque falten en la BD, no
        // deben contar como pendientes, de modo que el partido quede COMPLETO y no se reabra.
        var fixture = new FixtureProcessingAuditService.FixtureRow("m4", "france", "lnb",
                Instant.parse("2026-10-30T19:00:00Z"), "Local", "Visitante", true, true, false, true, false,
                false, true, false, true);

        var audit = FixtureProcessingAuditService.toAuditRow(fixture, LocalDate.of(2026, 11, 1));

        assertEquals("COMPLETO", audit.status());
        assertEquals(List.of(), audit.pendingDatasets());
    }

    @Test void excludesTodayRegardlessOfMatchTime() {
        var fixture = new FixtureProcessingAuditService.FixtureRow("m2", "spain", "liga-endesa",
                Instant.parse("2026-10-31T23:00:00Z"), "Local", "Visitante", false, false, false, false, false,
                false, false, false, false);

        var audit = FixtureProcessingAuditService.toAuditRow(fixture, LocalDate.of(2026, 11, 1));

        assertEquals("HOY_O_FUTURO", audit.status());
        assertEquals(List.of(), audit.pendingDatasets());
    }

    @Test void auditsResultsThatNeverReachedFixturesWithoutDuplicates() {
        String sql = FixtureProcessingAuditService.AUDIT_SQL.replaceAll("\\s+", " ");

        assertTrue(sql.contains("FROM fixtures.fixtures f UNION ALL"), sql);
        assertTrue(sql.contains("FROM results.results r WHERE NOT EXISTS (SELECT 1 FROM fixtures.fixtures f WHERE f.match_id = r.match_id)"), sql);
        assertTrue(sql.contains("FROM matches m"), sql);
    }

    @Test void mapsMissingResultsToMatchSummary() {
        var fixture = new FixtureProcessingAuditService.FixtureRow("m3", "spain", "liga-endesa",
                Instant.parse("2026-10-30T19:00:00Z"), "Local", "Visitante", false, true, true, true, true,
                false, false, false, false);

        var audit = FixtureProcessingAuditService.toAuditRow(fixture, LocalDate.of(2026, 11, 1));

        assertEquals("PENDIENTE", audit.status());
        assertEquals(List.of("MATCH_SUMMARY"), audit.pendingDatasets());
    }
}
