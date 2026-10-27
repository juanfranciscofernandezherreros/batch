package com.example.csvresults.batch;

import org.apache.commons.csv.CSVRecord;

public record ResultRow(long recordNumber, String matchId, String eventTime, String eventDatetime, String homeTeam, String awayTeam,
        Integer homeScore, Integer awayScore, Integer homeScore1, Integer homeScore2, Integer homeScore3,
    Integer homeScore4, Integer homeScore5, Integer awayScore1, Integer awayScore2, Integer awayScore3,
        Integer awayScore4, Integer awayScore5, String country, String competition) {
    static ResultRow parse(CSVRecord r, String country, String competition) {
        boolean legacy = r.size() == 16;
        boolean hasInvalidColumnCount = !legacy && r.size() != 17;
        if (hasInvalidColumnCount) throw new IllegalArgumentException("Invalid RESULTS column count recordNumber=" + r.getRecordNumber());
        ResultRow row = new ResultRow(r.getRecordNumber(), required(r, 0, "matchId"), optional(value(r, 1, legacy)),
                legacy ? null : optional(value(r, 2, false)), optional(value(r, 3, legacy)), optional(value(r, 4, legacy)),
                integer(r, 5, legacy), integer(r, 6, legacy), integer(r, 7, legacy), integer(r, 8, legacy), integer(r, 9, legacy),
                integer(r, 10, legacy), integer(r, 11, legacy), integer(r, 12, legacy), integer(r, 13, legacy), integer(r, 14, legacy),
                integer(r, 15, legacy), integer(r, 16, legacy), optional(country), optional(competition));
        validateScoreIntegrity(row);
        return row;
    }
    private static String required(CSVRecord r, int i, String name) { String v = r.get(i); boolean isBlank = v.isBlank(); if (isBlank) throw new IllegalArgumentException("Missing required field recordNumber=" + r.getRecordNumber() + " field=" + name); return v; }
    private static String value(CSVRecord r, int currentIndex, boolean legacy) { return r.get(legacy && currentIndex > 1 ? currentIndex - 1 : currentIndex); }
    private static String optional(String v) { return v == null || v.isBlank() ? null : v; }
    private static Integer integer(CSVRecord r, int i, boolean legacy) { String v = optional(value(r, i, legacy)); boolean hasNoValue = v == null; if (hasNoValue) return null; try { return Integer.valueOf(v); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid numeric score recordNumber=" + r.getRecordNumber() + " column=" + i, e); } }
    private static void validateScoreIntegrity(ResultRow row) {
        Integer[] home = {row.homeScore1(), row.homeScore2(), row.homeScore3(), row.homeScore4(), row.homeScore5()};
        Integer[] away = {row.awayScore1(), row.awayScore2(), row.awayScore3(), row.awayScore4(), row.awayScore5()};
        boolean hasMissingFinalScore = row.homeScore() == null || row.awayScore() == null;
        if (hasMissingFinalScore)
            throw new IllegalArgumentException("RESULTS requires final scores matchId=" + row.matchId());
        for (int i = 0; i < 4; i++) {
            boolean hasInvalidPeriodScore = home[i] == null || away[i] == null || home[i] < 0 || away[i] < 0;
            if (hasInvalidPeriodScore)
                throw new IllegalArgumentException("RESULTS requires non-negative Q" + (i + 1) + " scores for both teams matchId=" + row.matchId());
        }
        boolean hasUnpairedFifthPeriodScore = (home[4] == null) != (away[4] == null);
        if (hasUnpairedFifthPeriodScore)
            throw new IllegalArgumentException("RESULTS requires Q5 for both teams or neither matchId=" + row.matchId());
        boolean hasNegativeFifthPeriodScore = home[4] != null && (home[4] < 0 || away[4] < 0);
        if (hasNegativeFifthPeriodScore)
            throw new IllegalArgumentException("RESULTS requires non-negative Q5 scores matchId=" + row.matchId());
        int homeTotal = home[0] + home[1] + home[2] + home[3] + (home[4] == null ? 0 : home[4]);
        int awayTotal = away[0] + away[1] + away[2] + away[3] + (away[4] == null ? 0 : away[4]);
        boolean totalsDoNotMatchFinalScores = homeTotal != row.homeScore() || awayTotal != row.awayScore();
        if (totalsDoNotMatchFinalScores)
            throw new IllegalArgumentException("RESULTS totals do not match period scores matchId=" + row.matchId());
    }
    public String sourceEventId(String fileName) { return "csv-row-" + Integer.toHexString((fileName + "|" + recordNumber + "|" + matchId).hashCode()) + "-" + recordNumber; }
}
