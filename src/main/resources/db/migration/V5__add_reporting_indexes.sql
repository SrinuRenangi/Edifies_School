-- V5__add_reporting_indexes.sql
-- Staff Attendance Management System
-- Phase 8: Attendance History, Date Filters, Monthly Reports & Reporting Performance

-- 1. Composite index for filtering attendance records by work date and status
CREATE INDEX IF NOT EXISTS idx_attendance_work_date_status 
ON attendance_records (work_date DESC, status);

-- 2. Index for filtering and grouping staff by department in reports
CREATE INDEX IF NOT EXISTS idx_employees_department 
ON employees (department);

-- 3. Composite index for fast chronological sorting and pagination of employee history
CREATE INDEX IF NOT EXISTS idx_attendance_employee_chronological 
ON attendance_records (employee_id, work_date DESC, check_in_time DESC);