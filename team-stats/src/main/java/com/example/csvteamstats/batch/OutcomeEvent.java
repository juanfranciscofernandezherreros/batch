package com.example.csvteamstats.batch;

import java.sql.ResultSet;
import java.sql.SQLException;

public record OutcomeEvent(long id, String type, String sourceEventId, long persistedRecords,
        String matchId, String period, String category, String metric, String reason,
        String inputSource, int inputPartition, long inputOffset, long createdAt) {
    static OutcomeEvent map(ResultSet rs, int index) throws SQLException {
        return new OutcomeEvent(rs.getLong("id"), rs.getString("event_type"), rs.getString("source_event_id"),
                rs.getLong("persisted_records"), rs.getString("match_id"), rs.getString("period"),
                rs.getString("category"), rs.getString("metric"), rs.getString("reason_detail"),
                rs.getString("input_source"), rs.getInt("input_partition"), rs.getLong("input_offset"),
                rs.getLong("created_at"));
    }
}
