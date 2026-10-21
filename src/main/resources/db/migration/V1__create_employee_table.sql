-- V1__create_employee_table.sql
-- Staff Attendance Management System
-- Initial schema for Employee Management Module

CREATE TABLE IF NOT EXISTS employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(32) NOT NULL UNIQUE,
    full_name VARCHAR(128) NOT NULL,
    department VARCHAR(64) NOT NULL DEFAULT 'General',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Optimize search by Employee ID (exact match) and status
CREATE INDEX IF NOT EXISTS idx_employees_employee_id ON employees (employee_id);
CREATE INDEX IF NOT EXISTS idx_employees_status ON employees (status);

-- Optimize case-insensitive search by employee full name
CREATE INDEX IF NOT EXISTS idx_employees_full_name_lower ON employees (LOWER(full_name));
