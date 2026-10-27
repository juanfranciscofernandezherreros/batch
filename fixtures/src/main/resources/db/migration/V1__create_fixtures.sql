CREATE TABLE IF NOT EXISTS fixtures (match_id VARCHAR(255) NOT NULL, country VARCHAR(255) NOT NULL, competition VARCHAR(255) NOT NULL, source_event_id VARCHAR(255), event_time TIME WITHOUT TIME ZONE, home_team VARCHAR(255), away_team VARCHAR(255), PRIMARY KEY(match_id,country,competition));
CREATE INDEX IF NOT EXISTS idx_fixtures_source_event_id ON fixtures(source_event_id);
