package com.example.csvteamstats.batch;

import org.apache.commons.csv.CSVRecord;

/** Raw values have the same semantics as the existing parser/Avro contract. */
public record TeamStatsRow(long recordNumber, String matchId, String period, String category,
        String metric, String homeTeam, String homeValue, String awayTeam, String awayValue,
        String sourceUrl) {
    static TeamStatsRow parse(CSVRecord record) {
        if (record.size() != 9) {
            throw new IllegalArgumentException("Invalid TEAM_STATS column count recordNumber=" + record.getRecordNumber());
        }
        return new TeamStatsRow(record.getRecordNumber(), required(record, 0, "match_id"),
                required(record, 1, "period"), required(record, 2, "category"),
                required(record, 3, "metric"), required(record, 4, "home_team"), optional(record.get(5)),
                required(record, 6, "away_team"), optional(record.get(7)), optional(record.get(8)));
    }

    private static String required(CSVRecord record, int column, String name) {
        String value = record.get(column);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing required field recordNumber=" + record.getRecordNumber() + " field=" + name);
        }
        return value;
    }

    private static String optional(String value) { return value.isBlank() ? null : value; }

    public NaturalKey naturalKey() { return new NaturalKey(matchId, period, category, metric); }

    public String rejection() {
        String[] names = {"matchId", "period", "category", "metric", "homeTeam", "awayTeam", "homeValue", "awayValue"};
        String[] values = {matchId, period, category, metric, homeTeam, awayTeam, homeValue, awayValue};
        int[] limits = {128, 64, 128, 255, 255, 255, 255, 255};
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null && values[i].length() > limits[i]) {
                return names[i] + " length " + values[i].length() + " exceeds " + limits[i]
                        + " recordNumber=" + recordNumber;
            }
        }
        return null;
    }

    public record NaturalKey(String matchId, String period, String category, String metric) {}
}
