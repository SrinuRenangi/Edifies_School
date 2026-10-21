# ADR-005: Attendance Transaction & Photo Consistency Strategy
**Status:** APPROVED  
**Date:** 2026-10-21  

### Context and Problem Statement
Relational database commits and filesystem disk writes do not share an atomic distributed transaction. A strategy is required to guarantee that no orphaned database records exist without photos, and no orphaned photos fill the disk indefinitely.

### Selected Decision
**Approach A: Write Photo to Disk First, Coordinate via Spring TransactionSynchronizationManager, and Clean Up via Rollback Hook and Scheduled Sweep.**

### Workflow
1. The service writes the incoming image to `/photos/YYYY/MM/<uuid>.jpg`.
2. Registers a Spring `TransactionSynchronization` hook: if the database transaction rolls back, `afterCompletion()` immediately deletes the file from disk.
3. The database transaction executes: inserts `attendance_photos` metadata, creates/updates `attendance_sessions`, records the idempotency key, and flushes to PostgreSQL WAL.
4. If a server crash occurs before commit, a weekly scheduled cleanup worker sweeps unreferenced files older than 24 hours.

### Result
Guarantees zero false successes and self-healing data consistency without distributed lock managers.
