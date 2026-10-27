package com.example.csvbatchlauncher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BatchTypeTest {
    @Test void classifiesSupportedFiles() {
        assertEquals(BatchType.RESULTS, BatchType.from(Path.of("RESULTS_20261014120000_ES_ACB.csv")).orElseThrow());
        assertEquals(BatchType.FIXTURES, BatchType.from(Path.of("FIXTURES_20261014120000_ES_ACB.csv")).orElseThrow());
        assertEquals(BatchType.TEAM_STATS, BatchType.from(Path.of("STATS_ALL_PERIODS_20261014120000_m1.csv")).orElseThrow());
        assertEquals(BatchType.MATCH_SUMMARY, BatchType.from(Path.of("SCORE_SUMMARY_20261014120000_m1.csv")).orElseThrow());
        assertEquals(BatchType.STATS_PLAYER, BatchType.from(Path.of("PLAYER_STATS_20261014120000_m1.csv")).orElseThrow());
        assertEquals(BatchType.POINT_BY_POINT, BatchType.from(Path.of("POINT_BY_POINT_20261014120000_m1.csv")).orElseThrow());
        assertEquals(java.util.Optional.empty(), BatchType.from(Path.of("unknown.csv")));
    }
}
