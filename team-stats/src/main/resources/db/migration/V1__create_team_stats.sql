CREATE SEQUENCE IF NOT EXISTS team_stats_seq START WITH 1 INCREMENT BY 100;

CREATE TABLE IF NOT EXISTS team_stats (
    id BIGINT PRIMARY KEY DEFAULT nextval('team_stats_seq'),
    match_id VARCHAR(128) NOT NULL,
    period VARCHAR(64) NOT NULL,
    category VARCHAR(128) NOT NULL,
    metric VARCHAR(255) NOT NULL,
    home_team VARCHAR(255) NOT NULL,
    home_value VARCHAR(255),
    away_team VARCHAR(255) NOT NULL,
    away_value VARCHAR(255),
    source_url TEXT,
    source_event_id VARCHAR(255) NOT NULL,
    CONSTRAINT uk_team_stats_match_period_category_metric UNIQUE (match_id, period, category, metric)
);

CREATE INDEX IF NOT EXISTS idx_team_stats_match_id ON team_stats(match_id);
CREATE INDEX IF NOT EXISTS idx_team_stats_match_period ON team_stats(match_id, period);
