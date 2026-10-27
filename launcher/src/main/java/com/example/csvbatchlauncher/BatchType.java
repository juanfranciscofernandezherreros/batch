package com.example.csvbatchlauncher;

import java.nio.file.Path;
import com.example.csvbatchcore.CsvDataset;

public enum BatchType {
    RESULTS("results"), FIXTURES("fixtures"), TEAM_STATS("team-stats"), MATCH_SUMMARY("match-summary"),
    STATS_PLAYER("stats-player"), POINT_BY_POINT("point-by-point"), MATCH_DETAILS("match-details"), H2H("h2h"), ODDS("odds");
    private final String key;
    BatchType(String key) { this.key = key; }
    public String key() { return key; }
    public int priority() {
        return switch (this) {
            case RESULTS -> CsvDataset.RESULTS.order();
            case FIXTURES -> CsvDataset.FIXTURES.order();
            case MATCH_SUMMARY -> CsvDataset.SCORE_SUMMARY.order();
            case TEAM_STATS -> CsvDataset.STATS_ALL_PERIODS.order();
            case STATS_PLAYER -> CsvDataset.PLAYER_STATS.order();
            case POINT_BY_POINT -> CsvDataset.POINT_BY_POINT.order();
            default -> 100;
        };
    }
    public static BatchType from(Path path) {
        String n = path.getFileName().toString().toUpperCase(java.util.Locale.ROOT);
        if (n.matches("(?:RESULTS|RESULTS_.*)\\.CSV")) return RESULTS;
        if (n.matches("(?:FIXTURES|FIXTURES_.*)\\.CSV")) return FIXTURES;
        if (n.matches("(?:STATS_ALL_PERIODS|STATS_ALL_PERIODS_.*)\\.CSV")) return TEAM_STATS;
        if (n.matches("(?:MATCH_SUMMARY|MATCH_SUMMARY_.*|SCORE_SUMMARY_.*)\\.CSV")) return MATCH_SUMMARY;
        if (n.matches("(?:PLAYER_STATS|PLAYER_STATS_.*)\\.CSV")) return STATS_PLAYER;
        if (n.matches("(?:POINT_BY_POINT|POINT_BY_POINT_.*)\\.CSV")) return POINT_BY_POINT;
        if (n.matches("(?:MATCH_DETAILS|MATCH_DETAILS_.*)\\.CSV")) return MATCH_DETAILS;
        if (n.matches("(?:H2H|H2H_.*)\\.CSV")) return H2H;
        if (n.matches("ODDS_(?:HOME_AWAY|1X2|OVER_UNDER|ASIAN_HANDICAP)_.*\\.CSV")) return ODDS;
        return null;
    }
}
