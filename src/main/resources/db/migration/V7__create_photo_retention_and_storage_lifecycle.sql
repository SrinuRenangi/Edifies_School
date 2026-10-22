-- V7__create_photo_retention_and_storage_lifecycle.sql
-- Staff Attendance Management System
-- Phase 10: Photo Retention, Safe Cleanup, Storage Monitoring & Recovery

-- 1. Expand storage_status check constraint on attendance_photos
ALTER TABLE attendance_photos DROP CONSTRAINT IF EXISTS attendance_photos_storage_status_check;
ALTER TABLE attendance_photos ADD CONSTRAINT attendance_photos_storage_status_check 
    CHECK (storage_status IN ('ACTIVE', 'PENDING_DELETION', 'PURGED', 'MISSING', 'CLEANUP_FAILED', 'ARCHIVED'));

-- 2. Add retention tracking and recovery fields to attendance_photos
ALTER TABLE attendance_photos 
    ADD COLUMN IF NOT EXISTS purged_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cleanup_error VARCHAR(500),
    ADD COLUMN IF NOT EXISTS cleanup_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS retention_deadline TIMESTAMPTZ;

-- 3. Composite index for bounded retention queries and dry-run evaluation
CREATE INDEX IF NOT EXISTS idx_photo_retention 
ON attendance_photos (storage_status, created_at);

-- 4. Create photo retention policy configuration table
CREATE TABLE IF NOT EXISTS photo_retention_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    retention_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    retention_days INT NOT NULL DEFAULT 90 CHECK (retention_days >= 1 AND retention_days <= 3650),
    auto_cleanup_cron VARCHAR(64) NOT NULL DEFAULT '0 0 2 * * *',
    batch_size INT NOT NULL DEFAULT 50 CHECK (batch_size >= 1 AND batch_size <= 1000),
    temp_expiry_minutes INT NOT NULL DEFAULT 120 CHECK (temp_expiry_minutes >= 15),
    updated_by VARCHAR(128) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed initial policy (disabled by default per Business Requirement Section 4)
INSERT INTO photo_retention_policies (
    id, retention_enabled, retention_days, auto_cleanup_cron, batch_size, temp_expiry_minutes, updated_by
) VALUES (
    'c0000000-0000-0000-0000-000000000001',
    FALSE,
    90,
    '0 0 2 * * *',
    50,
    120,
    'INITIAL_SETUP'
)
ON CONFLICT (id) DO NOTHING;

-- 5. Create storage and photo cleanup audit history table
CREATE TABLE IF NOT EXISTS photo_cleanup_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_type VARCHAR(32) NOT NULL CHECK (run_type IN ('SCHEDULED', 'MANUAL', 'DRY_RUN')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    photos_evaluated INT NOT NULL DEFAULT 0,
    photos_deleted INT NOT NULL DEFAULT 0,
    photos_failed INT NOT NULL DEFAULT 0,
    photos_missing INT NOT NULL DEFAULT 0,
    bytes_reclaimed BIGINT NOT NULL DEFAULT 0,
    temp_files_cleaned INT NOT NULL DEFAULT 0,
    temp_bytes_reclaimed BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED')),
    error_message VARCHAR(500),
    performed_by VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cleanup_history_started_at 
ON photo_cleanup_history (started_at DESC);
