CREATE TABLE IF NOT EXISTS results (
    match_id VARCHAR(255) PRIMARY KEY,
    source_event_id VARCHAR(255) NOT NULL,
    event_time VARCHAR(255),
    home_team VARCHAR(255),
    away_team VARCHAR(255),
    home_score INTEGER,
    away_score INTEGER,
    home_score1 INTEGER,
    home_score2 INTEGER,
    home_score3 INTEGER,
    home_score4 INTEGER,
    home_score5 INTEGER,
    away_score1 INTEGER,
    away_score2 INTEGER,
    away_score3 INTEGER,
    away_score4 INTEGER,
    away_score5 INTEGER,
    country VARCHAR(255),
    competition VARCHAR(255),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_results_country_competition ON results (country, competition);
