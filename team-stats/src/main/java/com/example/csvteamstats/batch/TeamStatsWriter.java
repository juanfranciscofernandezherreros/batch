package com.example.csvteamstats.batch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

/** Uses the step transaction for business rows, outcome events and reader checkpoints. */
public class TeamStatsWriter implements ItemWriter<TeamStatsRow> {
    static final String UPSERT = """
            INSERT INTO team_stats (match_id, period, category, metric,
                home_team, home_value, away_team, away_value, source_url, source_event_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (match_id, period, category, metric) DO UPDATE SET
                home_team = EXCLUDED.home_team, home_value = EXCLUDED.home_value,
                away_team = EXCLUDED.away_team, away_value = EXCLUDED.away_value,
                source_url = EXCLUDED.source_url, source_event_id = EXCLUDED.source_event_id,
                updated_at = CURRENT_TIMESTAMP
            """;
    private final JdbcTemplate jdbc;
    private final long instanceId;
    private final String sourceEventId;
    private final String sourceName;
    private final int sourcePartition;
    private final long sourceOffset;

    public TeamStatsWriter(JdbcTemplate jdbc, long instanceId, String sourceEventId,
            String sourceName, int sourcePartition, long sourceOffset) {
        this.jdbc = jdbc;
        this.instanceId = instanceId;
        this.sourceEventId = sourceEventId;
        this.sourceName = sourceName;
        this.sourcePartition = sourcePartition;
        this.sourceOffset = sourceOffset;
    }

    @Override public void write(Chunk<? extends TeamStatsRow> chunk) {
        var valid = new LinkedHashMap<TeamStatsRow.NaturalKey, TeamStatsRow>();
        for (var row : chunk) {
            String rejection = row.rejection();
            if (rejection == null) valid.put(row.naturalKey(), row);
            else jdbc.update("""
                    INSERT INTO team_stats_batch.outcome_events (job_instance_id, event_type, source_event_id,
                        match_id, period, category, metric, reason_detail, input_source,
                        input_partition, input_offset, created_at)
                    VALUES (?, 'REJECTED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, instanceId, sourceEventId, row.matchId(), row.period(), row.category(), row.metric(),
                    rejection, sourceName, sourcePartition, sourceOffset, Instant.now().toEpochMilli());
        }
        // Last occurrence wins, as in the existing persistence application service.
        var rows = new ArrayList<>(valid.values());
        if (rows.isEmpty()) return;
        jdbc.batchUpdate(UPSERT, rows, rows.size(), (statement, row) -> {
            Object[] values = {row.matchId(), row.period(), row.category(), row.metric(), row.homeTeam(),
                    row.homeValue(), row.awayTeam(), row.awayValue(), row.sourceUrl(), sourceEventId};
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
        });
        jdbc.update("""
                INSERT INTO team_stats_batch.outcome_events (job_instance_id, event_type, source_event_id,
                    persisted_records, input_source, input_partition, input_offset, created_at)
                VALUES (?, 'SUCCESS', ?, ?, ?, ?, ?, ?)
                """, instanceId, sourceEventId, rows.size(), sourceName, sourcePartition, sourceOffset, Instant.now().toEpochMilli());
    }
}
