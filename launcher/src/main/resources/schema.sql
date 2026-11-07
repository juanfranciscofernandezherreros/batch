CREATE TABLE IF NOT EXISTS batch_launch_request (
  request_id VARCHAR(255) PRIMARY KEY,
  file_path TEXT NOT NULL,
  checksum VARCHAR(64) NOT NULL,
  batch_type VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  exit_code INTEGER,
  error TEXT
);
CREATE INDEX IF NOT EXISTS idx_batch_launch_request_status ON batch_launch_request(status, created_at);

CREATE TABLE IF NOT EXISTS csv_generated_file (
  file_path TEXT PRIMARY KEY,
  file_name TEXT NOT NULL,
  dataset VARCHAR(64) NOT NULL,
  country VARCHAR(128),
  competition VARCHAR(128),
  match_id VARCHAR(255),
  size_bytes BIGINT NOT NULL,
  modified_at TIMESTAMPTZ NOT NULL,
  discovered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_csv_generated_file_discovered_at ON csv_generated_file(discovered_at DESC);
