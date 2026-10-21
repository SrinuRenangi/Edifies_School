# Backend Module Design & Domain Boundaries
## Staff Attendance Management System — Phase 3 Technical Specification

**Document Version:** 1.0.0  
**Status:** Approved Module Specification  
**Architecture:** Modular Monolith (Package-by-Feature)

---

### 1. Backend Module Catalog (9 Modules)

#### Module 1: `auth-security`
- **Primary Responsibility:** Authentication, session tokens, password/PIN cryptographic verification, and rate-limiting enforcement.
- **Main Use Cases:** Admin login/logout, Kiosk PIN verification, JWT generation & validation, brute-force attempt tracking.
- **Business Rules Owned:** SEC-001 (BCrypt cost 12), SEC-002 (5 failed attempts locks for 15 min), SEC-003 (Admin JWT).
- **Entities Managed:** `AdminUser`, `KioskSessionToken`, `FailedAttemptTracker`.
- **Exposed APIs:** `POST /api/v1/auth/admin/login`, `POST /api/v1/auth/admin/logout`, `POST /api/v1/kiosk/verify-pin`.
- **Allowed Dependencies:** `employee-management` (to query PIN hash and active status).
- **Prohibited Dependencies:** `attendance-processing`, `reporting-export`, `photo-storage` (zero dependency on business reporting).
- **Testing Requirements:** 100% unit test coverage on PIN hashing and rate limiting; integration test for JWT lifecycle.

#### Module 2: `employee-management`
- **Primary Responsibility:** Employee directory, unique Employee ID enforcement, lifecycle status (Active/Inactive), and PIN administration.
- **Main Use Cases:** Register employee, update profile, deactivate/reactivate staff, admin PIN reset.
- **Business Rules Owned:** FR-011 (Unique ID), FR-012 (Deactivation without delete), FR-014 (Admin PIN reset).
- **Entities Managed:** `Employee` (UUID id, employee_id, full_name, department, pin_hash, status, created_at, updated_at).
- **Exposed APIs:** `GET /api/v1/admin/employees`, `POST /api/v1/admin/employees`, `PUT /api/v1/admin/employees/{id}`, `POST /api/v1/admin/employees/{id}/reset-pin`, `PATCH /api/v1/admin/employees/{id}/status`.
- **Allowed Dependencies:** `audit-corrections` (emits audit events).
- **Prohibited Dependencies:** `attendance-processing`, `photo-storage`.
- **Testing Requirements:** Integration tests verifying duplicate ID rejection, deactivation state transition, and FK restrict protection.

#### Module 3: `attendance-processing`
- **Primary Responsibility:** Core attendance engine, state machine enforcement, work-date cut-off calculation, idempotency, and punch recording.
- **Main Use Cases:** Punch Check-In, Punch Check-Out, Current State Query.
- **Business Rules Owned:** BR-001 (04:00 AM cut-off), BR-002 (Single session), BR-003 (Sequential punch), BR-006 (Server DB clock).
- **Entities Managed:** `AttendanceSession`, `IdempotencyRecord`.
- **Exposed APIs:** `GET /api/v1/kiosk/status/{employeeId}`, `POST /api/v1/kiosk/punch`.
- **Allowed Dependencies:** `employee-management` (verify active status), `photo-storage` (save photo metadata), `company-settings` (read cut-off hours), `audit-corrections`.
- **Prohibited Dependencies:** `reporting-export` (reporting queries must not couple to punch execution).
- **Testing Requirements:** Concurrency tests (simultaneous punches for same employee), state machine boundary tests, cut-off date transition tests.

#### Module 4: `photo-storage`
- **Primary Responsibility:** Inbound photo validation, EXIF stripping, JPEG compression, secure disk storage, and authenticated photo streaming.
- **Main Use Cases:** Save photo from punch payload, stream authorized photo to admin, purge expired photos.
- **Business Rules Owned:** BR-004 (Mandatory photo evidence), SEC-005 (Magic-byte verification), SEC-006 (2MB cap, 200KB target).
- **Entities Managed:** `AttendancePhoto` (id, storage_path, file_size_bytes, mime_type, sha256_hash, captured_at, purged_at).
- **Exposed APIs:** `GET /api/v1/photos/{photoId}` (requires Admin role).
- **Internal SPI:** `PhotoStorageService` interface (`saveImage()`, `retrieveImage()`, `deleteImage()`).
- **Allowed Dependencies:** None (zero coupling to domain entities).
- **Prohibited Dependencies:** `attendance-processing`, `employee-management`, `reporting-export`.
- **Testing Requirements:** Magic-byte rejection tests, path traversal security tests, image resize benchmark.

#### Module 5: `reporting-export`
- **Primary Responsibility:** High-performance historical queries, daily dashboard aggregations, monthly summaries, and streaming CSV generation.
- **Main Use Cases:** Daily attendance grid, employee date-range history, monthly summary calculations, CSV export.
- **Business Rules Owned:** FR-015 (Daily view), FR-016 (Monthly view), FR-020 (RFC-4180 CSV export).
- **Entities Managed:** Purely read-only query DTOs and projections.
- **Exposed APIs:** `GET /api/v1/admin/reports/daily`, `GET /api/v1/admin/reports/monthly`, `GET /api/v1/admin/reports/employee/{id}`, `GET /api/v1/admin/reports/export-csv`.
- **Allowed Dependencies:** Read-only access to `attendance-processing` and `employee-management` repositories.
- **Prohibited Dependencies:** Cannot mutate state in any module.
- **Testing Requirements:** Benchmark tests for monthly queries across 1,550 records (< 2s), CSV injection sanitization tests.

#### Module 6: `audit-corrections`
- **Primary Responsibility:** Administrative manual punch corrections and immutable audit logging.
- **Main Use Cases:** Admin adjust in/out time, admin manual status override, audit trail inspection.
- **Business Rules Owned:** BR-007 (Mandatory justification reason), SEC-007 (Append-only audit table).
- **Entities Managed:** `AuditLog` (id, entity_name, entity_id, action, actor_id, old_state, new_state, reason, timestamp).
- **Exposed APIs:** `POST /api/v1/admin/attendance/{sessionId}/adjust`, `GET /api/v1/admin/audit-logs`.
- **Allowed Dependencies:** `attendance-processing` (to apply approved adjustment).
- **Prohibited Dependencies:** None.
- **Testing Requirements:** Audit immutability tests (rejecting UPDATE/DELETE SQL statements), mandatory reason validation tests.

#### Module 7: `company-settings`
- **Primary Responsibility:** Configuration of organization-wide attendance policies, cut-off hours, and photo retention thresholds.
- **Main Use Cases:** Read current cut-off time (04:00 AM IST), update photo retention days (default 90).
- **Business Rules Owned:** DATA-002 (Configurable photo retention).
- **Entities Managed:** `CompanySetting`.
- **Exposed APIs:** `GET /api/v1/admin/settings`, `PUT /api/v1/admin/settings`.
- **Allowed Dependencies:** None.
- **Testing Requirements:** Validation tests preventing invalid cut-off times.

#### Module 8: `kiosk-management`
- **Primary Responsibility:** Kiosk device pairing, cryptographic token provisioning, device status telemetry, and deactivation.
- **Main Use Cases:** Generate pairing token, pair physical kiosk tablet, record kiosk heartbeat.
- **Business Rules Owned:** Kiosk pairing security and device trust.
- **Entities Managed:** `KioskDevice` (id, device_identifier, device_name, api_key_hash, is_active, last_heartbeat).
- **Exposed APIs:** `POST /api/v1/admin/kiosks/pair-token`, `POST /api/v1/kiosk/register`, `GET /api/v1/admin/kiosks`.
- **Allowed Dependencies:** `auth-security`.
- **Testing Requirements:** Token handshake verification tests.

#### Module 9: `operations-maintenance`
- **Primary Responsibility:** Background scheduled cron workers for forgotten checkout auto-closure and rolling photo purge.
- **Main Use Cases:** Auto-close unclosed sessions at 04:00 AM IST; purge raw photo files older than retention policy.
- **Business Rules Owned:** BR-005 (Forgotten checkout auto-closure), DATA-002 (Photo retention purge).
- **Entities Managed:** Interacts with `attendance-processing` and `photo-storage`.
- **Exposed APIs:** None (internal Spring `@Scheduled` tasks).
- **Allowed Dependencies:** `attendance-processing`, `photo-storage`, `company-settings`, `audit-corrections`.
- **Testing Requirements:** Scheduled execution tests verifying that old sessions transition to `AUTO_CLOSED` and photo files are purged from disk.

---

### 2. Prohibited Dependency Rules & Architecture Enforcement
To prevent the modular monolith from degenerating into an unmaintainable "big ball of mud", the following dependency rules are strictly enforced:
1. **No Circular Dependencies:** Circular package references (e.g., Module A $\rightarrow$ Module B $\rightarrow$ Module A) are strictly prohibited and will be verified via ArchUnit tests in Phase 19.
2. **Storage Isolation:** The `photo-storage` module must never import entities or repositories from `attendance-processing`. It only accepts byte streams and metadata, returning storage keys.
3. **Reporting Read-Only Boundary:** The `reporting-export` module must never execute `INSERT`, `UPDATE`, or `DELETE` operations.
