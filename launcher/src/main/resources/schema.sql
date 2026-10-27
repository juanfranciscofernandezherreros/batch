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
