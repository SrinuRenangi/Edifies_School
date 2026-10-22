-- V6__create_attendance_corrections.sql
-- Staff Attendance Management System
-- Phase 9: Attendance Corrections, Missed Punches, Manual Entries & Immutable Audit History

-- 1. Update status constraint on attendance_records to include 'VOIDED'
ALTER TABLE attendance_records DROP CONSTRAINT IF EXISTS attendance_records_status_check;
ALTER TABLE attendance_records ADD CONSTRAINT attendance_records_status_check 
    CHECK (status IN ('CHECKED_IN', 'CHECKED_OUT', 'AUTO_CLOSED', 'VOIDED'));

-- 2. Update Invariant 2 (single daily record) to exclude voided records
DROP INDEX IF EXISTS uk_employee_work_date;
CREATE UNIQUE INDEX uk_employee_work_date 
ON attendance_records (employee_id, work_date) 
WHERE status != 'VOIDED';

-- 3. Add correction tracking flags to attendance_records
ALTER TABLE attendance_records 
    ADD COLUMN IF NOT EXISTS is_corrected BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_manual_entry BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_correction_at TIMESTAMPTZ;

-- 4. Create immutable attendance corrections audit table
CREATE TABLE IF NOT EXISTS attendance_corrections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attendance_record_id UUID NOT NULL REFERENCES attendance_records(id) ON DELETE RESTRICT,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    action_type VARCHAR(64) NOT NULL,
    previous_check_in_time TIMESTAMPTZ,
    corrected_check_in_time TIMESTAMPTZ,
    previous_check_out_time TIMESTAMPTZ,
    corrected_check_out_time TIMESTAMPTZ,
    previous_status VARCHAR(32),
    corrected_status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    performed_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    record_version BIGINT NOT NULL
);

-- Supporting indexes for fast audit querying and chronological timelines
CREATE INDEX IF NOT EXISTS idx_corrections_record_id 
ON attendance_corrections (attendance_record_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_corrections_employee_id 
ON attendance_corrections (employee_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_corrections_created_at 
ON attendance_corrections (created_at DESC);
