package com.example.csvbatchlauncher;

import java.nio.file.Path;
import java.util.Optional;
import com.example.csvbatchcore.CsvDataset;

public enum BatchType {
    RESULTS("results", false), FIXTURES("fixtures", false), TEAM_STATS("team-stats", true), MATCH_SUMMARY("match-summary", false),
    STATS_PLAYER("stats-player", true), POINT_BY_POINT("point-by-point", true), MATCH_DETAILS("match-details", false), H2H("h2h", false), ODDS("odds", false);
    private final String key;
    private final boolean requiresResultsMatch;
    BatchType(String key, boolean requiresResultsMatch) { this.key = key; this.requiresResultsMatch = requiresResultsMatch; }
    public String key() { return key; }
    public boolean requiresResultsMatch() { return requiresResultsMatch; }
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
    public static Optional<BatchType> from(Path path) {
        String n = path.getFileName().toString().toUpperCase(java.util.Locale.ROOT);
        return java.util.Arrays.stream(values()).filter(type -> type.matches(n)).findFirst();
    }
    private boolean matches(String name) { return switch (this) {
        case RESULTS -> name.matches("(?:RESULTS|RESULTS_.*)\\.CSV");
        case FIXTURES -> name.matches("(?:FIXTURES|FIXTURES_.*)\\.CSV");
        case TEAM_STATS -> name.matches("(?:STATS_ALL_PERIODS|STATS_ALL_PERIODS_.*)\\.CSV");
        case MATCH_SUMMARY -> name.matches("(?:MATCH_SUMMARY|MATCH_SUMMARY_.*|SCORE_SUMMARY_.*)\\.CSV");
        case STATS_PLAYER -> name.matches("(?:PLAYER_STATS|PLAYER_STATS_.*)\\.CSV");
        case POINT_BY_POINT -> name.matches("(?:POINT_BY_POINT|POINT_BY_POINT_.*)\\.CSV");
        case MATCH_DETAILS -> name.matches("(?:MATCH_DETAILS|MATCH_DETAILS_.*)\\.CSV");
        case H2H -> name.matches("(?:H2H|H2H_.*)\\.CSV");
        case ODDS -> name.matches("ODDS_(?:HOME_AWAY|1X2|OVER_UNDER|ASIAN_HANDICAP)_.*\\.CSV");
    }; }
}
