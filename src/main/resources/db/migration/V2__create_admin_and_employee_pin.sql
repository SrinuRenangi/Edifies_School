-- V2__create_admin_and_employee_pin.sql
-- Staff Attendance Management System
-- Phase 5: Administrator Security & Employee PIN Authentication

-- 1. Create table for Administrator accounts
CREATE TABLE IF NOT EXISTS admin_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'ROLE_ADMIN',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_users_username ON admin_users (LOWER(username));

-- 2. Seed default Administrator: admin / Admin@12345 (BCrypt cost 12)
INSERT INTO admin_users (id, username, password_hash, full_name, role, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'admin',
    '$2a$12$vRWdZjVPhNh4xKYcgwTUjuumY7jpqyCLVentztPPPB2CnTPKAjMty',
    'System Administrator',
    'ROLE_ADMIN',
    TRUE
)
ON CONFLICT (username) DO NOTHING;

-- 3. Add PIN hash column to employees table
ALTER TABLE employees ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(255);

-- 4. Seed any existing employees with default PIN '1234' hash
UPDATE employees 
SET pin_hash = '$2a$12$oIBDQHN1b86q.xvq1Ko/FONEUfMSNAlaCXBw64dIb1462elrb2dhq'
WHERE pin_hash IS NULL;

-- 5. Enforce NOT NULL on pin_hash
ALTER TABLE employees ALTER COLUMN pin_hash SET NOT NULL;
