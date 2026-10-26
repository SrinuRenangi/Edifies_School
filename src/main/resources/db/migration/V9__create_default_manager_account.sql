-- V9__create_default_manager_account.sql
-- Staff Attendance Management System
-- Phase 17: Role-Based Access Control - Manager Account Provisioning

-- 1. Ensure role check constraint allows ROLE_ADMIN and ROLE_MANAGER
ALTER TABLE admin_users DROP CONSTRAINT IF EXISTS chk_admin_users_role;
ALTER TABLE admin_users ADD CONSTRAINT chk_admin_users_role 
    CHECK (role IN ('ROLE_ADMIN', 'ROLE_MANAGER'));

-- 2. Index for role-based administrative queries
CREATE INDEX IF NOT EXISTS idx_admin_users_role ON admin_users (role);
