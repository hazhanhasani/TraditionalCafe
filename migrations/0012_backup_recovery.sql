CREATE TABLE IF NOT EXISTS backup_snapshots (
  id TEXT PRIMARY KEY,
  kind TEXT NOT NULL CHECK (kind IN ('manual','daily','pre_delete','pre_restore')),
  reason TEXT,
  status TEXT NOT NULL DEFAULT 'ready' CHECK (status IN ('creating','ready','failed','restored')),
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TEXT,
  table_count INTEGER NOT NULL DEFAULT 0,
  row_count INTEGER NOT NULL DEFAULT 0,
  size_bytes INTEGER NOT NULL DEFAULT 0,
  checksum TEXT,
  metadata TEXT,
  FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS backup_snapshot_chunks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  snapshot_id TEXT NOT NULL,
  table_name TEXT NOT NULL,
  chunk_index INTEGER NOT NULL DEFAULT 0,
  row_count INTEGER NOT NULL DEFAULT 0,
  payload TEXT NOT NULL,
  checksum TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (snapshot_id) REFERENCES backup_snapshots(id) ON DELETE CASCADE,
  UNIQUE (snapshot_id, table_name, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_backup_snapshots_created_at
ON backup_snapshots(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_backup_snapshots_kind
ON backup_snapshots(kind, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_backup_chunks_snapshot
ON backup_snapshot_chunks(snapshot_id, table_name, chunk_index);
