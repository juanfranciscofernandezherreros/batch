package com.example.csvbatchlauncher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BatchTypeTest {
    @Test void classifiesSupportedFiles() {
        assertEquals(BatchType.RESULTS, BatchType.from(Path.of("RESULTS_20261014120000_ES_ACB.csv")));
        assertEquals(BatchType.FIXTURES, BatchType.from(Path.of("FIXTURES_20261014120000_ES_ACB.csv")));
        assertEquals(BatchType.TEAM_STATS, BatchType.from(Path.of("STATS_ALL_PERIODS_20261014120000_m1.csv")));
        assertEquals(BatchType.MATCH_SUMMARY, BatchType.from(Path.of("SCORE_SUMMARY_20261014120000_m1.csv")));
        assertEquals(BatchType.STATS_PLAYER, BatchType.from(Path.of("PLAYER_STATS_20261014120000_m1.csv")));
        assertEquals(BatchType.POINT_BY_POINT, BatchType.from(Path.of("POINT_BY_POINT_20261014120000_m1.csv")));
        assertNull(BatchType.from(Path.of("unknown.csv")));
    }
}
