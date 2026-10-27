package com.example.csvbatchcore;

import java.util.List;
import java.util.Map;

/** Shared, discoverable contracts for the mandatory ingestion datasets. */
public final class CsvContracts {
    private CsvContracts() { }
    public static final Map<CsvDataset, CsvContract> REQUIRED = Map.of(
        CsvDataset.RESULTS, new CsvContract(CsvDataset.RESULTS, "RESULTS_*.csv", "results", List.of("matchId", "homeTeam", "awayTeam")),
        CsvDataset.FIXTURES, new CsvContract(CsvDataset.FIXTURES, "FIXTURES_*.csv", "fixtures", List.of("matchId", "homeTeam", "awayTeam")),
        CsvDataset.SCORE_SUMMARY, new CsvContract(CsvDataset.SCORE_SUMMARY, "SCORE_SUMMARY_*.csv", "match_summary", List.of("matchId")),
        CsvDataset.STATS_ALL_PERIODS, new CsvContract(CsvDataset.STATS_ALL_PERIODS, "STATS_ALL_PERIODS_*.csv", "team_stats", List.of("matchId")),
        CsvDataset.PLAYER_STATS, new CsvContract(CsvDataset.PLAYER_STATS, "PLAYER_STATS_*.csv", "stats_player", List.of("matchId")),
        CsvDataset.POINT_BY_POINT, new CsvContract(CsvDataset.POINT_BY_POINT, "POINT_BY_POINT_*.csv", "point_by_point", List.of("matchId"))
    );
}
