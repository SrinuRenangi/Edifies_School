-- V4__create_attendance_photos.sql
-- Staff Attendance Management System
-- Phase 7: Camera Capture, Photo Validation, Secure Photo Storage & Evidence

-- 1. Create attendance photos metadata table
CREATE TABLE IF NOT EXISTS attendance_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attendance_id UUID NOT NULL REFERENCES attendance_records(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    photo_type VARCHAR(16) NOT NULL CHECK (photo_type IN ('CHECK_IN', 'CHECK_OUT')),
    storage_path VARCHAR(512) NOT NULL,
    file_name VARCHAR(128) NOT NULL,
    mime_type VARCHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    image_width INT NOT NULL,
    image_height INT NOT NULL,
    storage_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (storage_status IN ('ACTIVE', 'ARCHIVED', 'PURGED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_attendance_photo_type UNIQUE (attendance_id, photo_type)
);

-- Indexes for performance and lifecycle queries
CREATE INDEX IF NOT EXISTS idx_photo_attendance_id ON attendance_photos (attendance_id);
CREATE INDEX IF NOT EXISTS idx_photo_employee_id ON attendance_photos (employee_id);
CREATE INDEX IF NOT EXISTS idx_photo_created_at ON attendance_photos (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_photo_status ON attendance_photos (storage_status);

-- 2. Add photo existence indicators to attendance_records for optimal query performance
ALTER TABLE attendance_records 
    ADD COLUMN IF NOT EXISTS has_check_in_photo BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS has_check_out_photo BOOLEAN NOT NULL DEFAULT FALSE;
