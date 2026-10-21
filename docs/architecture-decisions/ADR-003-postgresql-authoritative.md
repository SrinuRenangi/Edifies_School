# ADR-003: PostgreSQL 16 as Single Authoritative Datastore
**Status:** APPROVED  
**Date:** 2026-10-21  

### Context and Problem Statement
Attendance records represent official employment and legal compliance history. The datastore must guarantee ACID atomicity, partial indexing, JSONB auditing, and zero corruption across 3+ years of records.

### Selected Decision
**PostgreSQL 16** as the single authoritative relational datastore.

### Reasons for Selection
1. **Strict Partial Unique Indexes:** Enables `CREATE UNIQUE INDEX uk_single_open_session ON attendance_sessions (employee_id) WHERE out_time IS NULL AND status = 'INCOMPLETE'`, guaranteeing at the database engine level that no employee can open two concurrent sessions.
2. **JSONB Audit Logging:** Native JSONB columns store historical entity snapshots with minimal space overhead and fast indexing.
3. **Write-Ahead Logging (WAL):** Industry-standard durability guaranteeing zero committed transaction loss across host reboots.
4. **Zero Licensing Cost:** Open-source, robust community support, excellent tooling (pg_dump, pg_restore).
