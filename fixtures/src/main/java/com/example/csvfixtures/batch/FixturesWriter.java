package com.example.csvfixtures.batch;
import org.springframework.batch.item.Chunk; import org.springframework.batch.item.ItemWriter; import org.springframework.jdbc.core.JdbcTemplate;
public class FixturesWriter implements ItemWriter<FixtureRow> {
    static final String UPSERT = "INSERT INTO fixtures (match_id, country, competition, source_event_id, event_time, event_datetime, home_team, away_team) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (match_id) DO UPDATE SET country=EXCLUDED.country, competition=EXCLUDED.competition, source_event_id=EXCLUDED.source_event_id, event_time=EXCLUDED.event_time, event_datetime=EXCLUDED.event_datetime, home_team=EXCLUDED.home_team, away_team=EXCLUDED.away_team";
    private final JdbcTemplate jdbc; private final String sourceFile; public FixturesWriter(JdbcTemplate jdbc, String sourceFile) { this.jdbc = jdbc; this.sourceFile = sourceFile; }
    @Override public void write(Chunk<? extends FixtureRow> chunk) { if (chunk.isEmpty()) return; jdbc.batchUpdate(UPSERT, chunk.getItems(), chunk.size(), (s, r) -> { Object[] v = {r.matchId(), r.country(), r.competition(), r.sourceEventId(sourceFile), r.eventTime(), r.eventDatetime(), r.homeTeam(), r.awayTeam()}; for (int i = 0; i < v.length; i++) s.setObject(i + 1, v[i]); }); }
}
