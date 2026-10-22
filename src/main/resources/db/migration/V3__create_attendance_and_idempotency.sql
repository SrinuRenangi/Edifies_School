-- V3__create_attendance_and_idempotency.sql
-- Staff Attendance Management System
-- Phase 6: Attendance Engine, State Machine, Concurrency & Idempotency

-- 1. Create attendance records table
CREATE TABLE IF NOT EXISTS attendance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    work_date DATE NOT NULL,
    check_in_time TIMESTAMPTZ NOT NULL,
    check_out_time TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL CHECK (status IN ('CHECKED_IN', 'CHECKED_OUT', 'AUTO_CLOSED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Invariant 1: Single Open Session Guarantee
-- An employee can have AT MOST ONE active open session across the entire database.
CREATE UNIQUE INDEX IF NOT EXISTS uk_single_open_session 
ON attendance_records (employee_id) 
WHERE status = 'CHECKED_IN';

-- Invariant 2: Single Daily Record Guarantee
-- An employee can have AT MOST ONE attendance record per work_date (single shift per day policy).
CREATE UNIQUE INDEX IF NOT EXISTS uk_employee_work_date 
ON attendance_records (employee_id, work_date);

-- Query optimizations for reporting and administrative history
CREATE INDEX IF NOT EXISTS idx_attendance_employee_id ON attendance_records (employee_id);
CREATE INDEX IF NOT EXISTS idx_attendance_work_date ON attendance_records (work_date DESC);
CREATE INDEX IF NOT EXISTS idx_attendance_status ON attendance_records (status);
CREATE INDEX IF NOT EXISTS idx_attendance_check_in_time ON attendance_records (check_in_time DESC);

-- 2. Create idempotency tracking table
CREATE TABLE IF NOT EXISTS idempotency_keys (
    key_value VARCHAR(128) PRIMARY KEY,
    endpoint VARCHAR(128) NOT NULL,
    employee_id UUID,
    status_code INT NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_idempotency_created_at ON idempotency_keys (created_at);
