CREATE SCHEMA IF NOT EXISTS match_details;
CREATE SCHEMA IF NOT EXISTS h2h;
CREATE SCHEMA IF NOT EXISTS odds;

CREATE TABLE IF NOT EXISTS match_details.match_details (
    match_id TEXT NOT NULL,
    view_name TEXT NOT NULL,
    record_type TEXT NOT NULL,
    label TEXT NOT NULL,
    value_text TEXT,
    link_url TEXT,
    source_url TEXT,
    source_file TEXT NOT NULL,
    row_hash TEXT NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (match_id, row_hash)
);

CREATE TABLE IF NOT EXISTS h2h.h2h (
    match_id TEXT NOT NULL,
    section TEXT,
    event_date TEXT,
    competition TEXT,
    home_team TEXT,
    away_team TEXT,
    note TEXT,
    home_score TEXT,
    away_score TEXT,
    result_for_section_team TEXT,
    source_url TEXT,
    source_file TEXT NOT NULL,
    row_hash TEXT NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (match_id, row_hash)
);

CREATE TABLE IF NOT EXISTS odds.odds (
    match_id TEXT NOT NULL,
    market_type TEXT NOT NULL,
    market TEXT,
    period TEXT,
    bookmaker TEXT,
    bookmaker_id TEXT,
    line TEXT,
    home_line TEXT,
    away_line TEXT,
    home_odds TEXT,
    draw_odds TEXT,
    away_odds TEXT,
    over_odds TEXT,
    under_odds TEXT,
    raw_text TEXT,
    source_url TEXT,
    source_file TEXT NOT NULL,
    row_hash TEXT NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (match_id, market_type, row_hash)
);

CREATE INDEX IF NOT EXISTS idx_match_details_match_id ON match_details.match_details(match_id);
CREATE INDEX IF NOT EXISTS idx_h2h_match_id ON h2h.h2h(match_id);
CREATE INDEX IF NOT EXISTS idx_odds_match_id ON odds.odds(match_id);
