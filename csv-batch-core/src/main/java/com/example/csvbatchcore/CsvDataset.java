package com.example.csvbatchcore;

/** Canonical datasets and their required global ingestion order. */
public enum CsvDataset {
    RESULTS(10), FIXTURES(20), SCORE_SUMMARY(30), STATS_ALL_PERIODS(40), PLAYER_STATS(50), POINT_BY_POINT(60);

    private final int order;
    CsvDataset(int order) { this.order = order; }
    public int order() { return order; }
}
