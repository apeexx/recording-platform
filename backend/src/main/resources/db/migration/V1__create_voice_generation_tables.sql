CREATE TABLE IF NOT EXISTS voice_generation_records (
	id VARCHAR(36) PRIMARY KEY,
	mode VARCHAR(32) NOT NULL CHECK (mode IN ('PREVIEW', 'SYNTHESIZE', 'CLONE')),
	status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
	text TEXT,
	voice_id VARCHAR(128),
	speed DOUBLE PRECISION NOT NULL,
	volume DOUBLE PRECISION NOT NULL,
	pitch INTEGER NOT NULL,
	audio_path TEXT,
	audio_format VARCHAR(16),
	duration_millis BIGINT NOT NULL,
	message TEXT,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_voice_generation_records_created_at
	ON voice_generation_records (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_voice_generation_records_status
	ON voice_generation_records (status);

CREATE INDEX IF NOT EXISTS idx_voice_generation_records_mode
	ON voice_generation_records (mode);

CREATE INDEX IF NOT EXISTS idx_voice_generation_records_voice_id
	ON voice_generation_records (voice_id);

CREATE TABLE IF NOT EXISTS voice_generation_configs (
	id VARCHAR(64) PRIMARY KEY,
	voice_id VARCHAR(128) NOT NULL,
	speed DOUBLE PRECISION NOT NULL,
	volume DOUBLE PRECISION NOT NULL,
	pitch INTEGER NOT NULL,
	updated_at TIMESTAMP WITH TIME ZONE
);
