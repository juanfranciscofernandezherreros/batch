package com.example.csvresults.batch;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public class ResultsWriter implements ItemWriter<ResultRow> {
    static final String UPSERT = """
        INSERT INTO results (match_id, source_event_id, event_time, event_datetime, home_team, away_team, home_score, away_score,
          home_score1, home_score2, home_score3, home_score4, home_score5, away_score1, away_score2, away_score3,
          away_score4, away_score5, country, competition, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT (match_id) DO UPDATE SET source_event_id=EXCLUDED.source_event_id, event_time=EXCLUDED.event_time, event_datetime=EXCLUDED.event_datetime,
          home_team=EXCLUDED.home_team, away_team=EXCLUDED.away_team, home_score=EXCLUDED.home_score, away_score=EXCLUDED.away_score,
          home_score1=EXCLUDED.home_score1, home_score2=EXCLUDED.home_score2, home_score3=EXCLUDED.home_score3,
          home_score4=EXCLUDED.home_score4, home_score5=EXCLUDED.home_score5, away_score1=EXCLUDED.away_score1,
          away_score2=EXCLUDED.away_score2, away_score3=EXCLUDED.away_score3, away_score4=EXCLUDED.away_score4,
          away_score5=EXCLUDED.away_score5, country=EXCLUDED.country, competition=EXCLUDED.competition, updated_at=CURRENT_TIMESTAMP
        """;
    private final JdbcTemplate jdbc; private final String sourceFile;
    public ResultsWriter(JdbcTemplate jdbc, String sourceFile) { this.jdbc = jdbc; this.sourceFile = sourceFile; }
    @Override public void write(Chunk<? extends ResultRow> chunk) { boolean hasNoRows = chunk.isEmpty(); if (hasNoRows) return; jdbc.batchUpdate(UPSERT, chunk.getItems(), chunk.size(), (s, r) -> {
        Object[] v = {r.matchId(), r.sourceEventId(sourceFile), eventTime(r.eventTime()), eventDatetime(r.eventDatetime()), r.homeTeam(), r.awayTeam(), r.homeScore(), r.awayScore(), r.homeScore1(), r.homeScore2(), r.homeScore3(), r.homeScore4(), r.homeScore5(), r.awayScore1(), r.awayScore2(), r.awayScore3(), r.awayScore4(), r.awayScore5(), r.country(), r.competition()};
        for (int i = 0; i < v.length; i++) s.setObject(i + 1, v[i]);
    }); }
    private static LocalTime eventTime(String value) { return value == null || value.isBlank() ? null : LocalTime.parse(value); }
    private static OffsetDateTime eventDatetime(String value) { return value == null || value.isBlank() ? null : OffsetDateTime.parse(value); }
}
