# Functional, Non-Functional, and Security Requirements Specification
## Staff Attendance Management System — Phase 2 Requirements Register

**Document Version:** 1.0.0  
**Status:** Approved Requirements Baseline (Phase 2)  
**System Target:** Modular Monolith (Spring Boot 3 + Angular 18 + PostgreSQL 16)  
**Primary Timezone:** `Asia/Kolkata` (IST, UTC+05:30)

---

### 1. Functional Requirements Register (FR)

| Req ID | Title | Description | Category | Priority | Acceptance Criteria | Dependency | Arch Component | Target Phase | Verification Method |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **FR-001** | Employee Identification | Kiosk shall accept an alphanumeric Employee ID and a 4-to-6 digit numeric PIN. | Kiosk Auth | Must Have | Validates format; rejects empty/invalid formats with generic message. | None | `auth-security` | Phase 4, 14 | Automated Unit Test |
| **FR-002** | PIN Verification | Backend shall verify the submitted PIN against the salted BCrypt hash. | Kiosk Auth | Must Have | Returns short-lived session token (max 60s) on success; increments failure counter on mismatch. | FR-001 | `auth-security` | Phase 4 | Integration Test |
| **FR-003** | Attendance Status Query | System shall query current attendance state upon successful PIN verification. | Attendance | Must Have | Returns `NOT_CHECKED_IN` or `CHECKED_IN` along with the permitted action. | FR-002 | `attendance-engine` | Phase 8, 14 | API Test |
| **FR-004** | Live Camera Preview | Kiosk frontend shall access device camera using Web MediaStreams API and display real-time preview. | Kiosk UX | Must Have | Video stream active; explicit user click triggers frame capture to canvas. | None | `frontend-kiosk` | Phase 15 | Manual / Browser E2E |
| **FR-005** | Photo Capture & Review | Employee must preview the captured still image and can retake or confirm submission. | Kiosk UX | Must Have | Canvas converts frame to JPEG Blob (max 2MB); user confirms before upload. | FR-004 | `frontend-kiosk` | Phase 15 | Browser E2E |
| **FR-006** | Check-In Punch Submission | Kiosk shall submit Check-In request containing employee token, idempotency key, and photo blob. | Attendance | Must Have | Server creates new `attendance_sessions` row with server timestamp; returns 201 Created. | FR-003, FR-005 | `attendance-engine` | Phase 9, 14 | Integration Test |
| **FR-007** | Check-Out Punch Submission | Kiosk shall submit Check-Out request matching open session. | Attendance | Must Have | Server updates existing session with `out_time`, `out_photo_id`, status `PRESENT`. | FR-003, FR-005 | `attendance-engine` | Phase 9, 14 | Integration Test |
| **FR-008** | Server-Authoritative Timestamp | Attendance in/out times must be generated strictly on backend DB server in `Asia/Kolkata`. | Integrity | Must Have | Client clock ignored; timestamp derived from DB `CURRENT_TIMESTAMP`. | FR-006, FR-007 | `attendance-engine` | Phase 8 | DB Integration Test |
| **FR-009** | Idempotency Protection | Server shall reject duplicate punch submissions using client-generated UUID idempotency keys. | Integrity | Must Have | Replaying same payload returns original result without second insert; modified payload returns 409. | FR-006, FR-007 | `attendance-engine` | Phase 9 | Concurrency Test |
| **FR-010** | Kiosk Auto-Reset | Kiosk UI shall automatically clear in-memory tokens and return to start screen after punch or inactivity. | Kiosk UX | Must Have | 3-second display of success banner on punch; 15-second inactivity timeout on idle/pin entry. | FR-006, FR-007 | `frontend-kiosk` | Phase 14 | Timer E2E Test |
| **FR-011** | Employee Registration | Admin shall register an employee with Unique Employee ID, Full Name, Department, and Initial PIN. | Employee | Must Have | Rejects duplicate Employee ID; hashes PIN; saves with status `ACTIVE`. | None | `employee-mgmt` | Phase 5, 16 | Unit/Integration Test |
| **FR-012** | Employee Deactivation | Admin can deactivate an employee without cascading delete of historical attendance records. | Employee | Must Have | Sets `status = 'INACTIVE'`; prevents future kiosk punches; preserves history. | FR-011 | `employee-mgmt` | Phase 5, 16 | Regression Test |
| **FR-013** | Employee Search | Admin can search employees by exact Employee ID or partial name substring. | Employee | Must Have | Search case-insensitive; returns active and inactive filter options. | FR-011 | `employee-mgmt` | Phase 5, 16 | API Test |
| **FR-014** | Admin PIN Reset | Admin can trigger a secure PIN reset for an employee without viewing current PIN. | Employee | Must Have | Sets new temporary PIN; requires change or hashes directly; clears lockouts. | FR-011 | `employee-mgmt` | Phase 5, 16 | Integration Test |
| **FR-015** | Daily Attendance Dashboard | Admin can view all attendance sessions for a selected calendar date in `Asia/Kolkata`. | Reporting | Must Have | Lists Employee ID, Name, In-Time, Out-Time, Status, and Photo thumbnails. | FR-006, FR-007 | `reporting-audit` | Phase 11, 17 | Integration Test |
| **FR-016** | Date Range & Monthly History | Admin can query historical records for an individual employee across custom dates or full month. | Reporting | Must Have | Computes days worked, completed sessions, incomplete sessions; renders data table. | FR-015 | `reporting-audit` | Phase 11, 17 | DB Query Test |
| **FR-017** | Authenticated Photo Viewing | Admin can view full-resolution attendance photos via secure streaming endpoint. | Storage | Must Have | Endpoint checks Admin JWT; rejects unauthenticated access; streams image with caching headers. | FR-005 | `photo-storage` | Phase 7, 17 | Security Test |
| **FR-018** | Administrative Manual Adjustment | Admin can adjust check-in/out times or session status with a mandatory justification reason. | Audit | Must Have | Updates record, increments `@Version`, inserts full audit trail row. | FR-015 | `reporting-audit` | Phase 10, 18 | Audit Test |
| **FR-019** | Audit Trail Inspection | Admin can inspect audit logs for any attendance session or employee profile. | Audit | Must Have | Shows timestamp, modifying admin, previous state, new state, reason. | FR-018 | `reporting-audit` | Phase 10, 18 | API Test |
| **FR-020** | CSV Report Export | Admin can export filtered daily or monthly attendance data to RFC-4180 compliant CSV. | Reporting | Must Have | Correct headers; sanitized against CSV injection; proper date formatting. | FR-016 | `reporting-audit` | Phase 11, 18 | File Export Test |

---

### 2. Non-Functional Requirements Register (NFR)

| Req ID | Title | Description | Target Metric / Acceptance Threshold | Target Phase | Verification Method |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **NFR-001** | Kiosk Punch Response Time | Total time from employee tapping "Confirm" to displaying success confirmation. | ≤ 3.0 seconds (on standard local network after image capture). | Phase 9, 19 | Load / Benchmark Test |
| **NFR-002** | Search Query Latency | Execution time for employee search and single-day attendance query. | ≤ 500 ms for database lookup; ≤ 1.5s total HTTP roundtrip. | Phase 11 | DB Query Plan / Benchmark |
| **NFR-003** | Monthly Report Generation | Aggregation latency for 20-50 employees over 31 days (up to 1,550 sessions). | ≤ 2.0 seconds end-to-end. | Phase 11 | Benchmark Test |
| **NFR-004** | Data Durability & Recovery | Attendance data and audit logs must survive abrupt application/host restart. | Zero data loss for committed transactions (PostgreSQL WAL flush). | Phase 3, 20 | Chaos/Restart Test |
| **NFR-005** | 3-Year Storage Headroom | System storage capacity must accommodate 3 full years of data and backups. | DB: < 500MB; Photos (with 90-day rolling purge): < 5GB; Total disk: 50GB allocated. | Phase 12, 20 | Capacity Audit |
| **NFR-006** | Responsive Interface | UI layout must seamlessly adapt across desktop, tablet, and smartphone browsers. | Fluid responsive rendering from 360px viewport width up to 4K displays. | Phase 13, 16 | Multi-device Browser Testing |
| **NFR-007** | Touch-Target Ergonomics | Interactive elements on Kiosk UI must meet mobile accessibility standards. | Minimum touch target 48x48 CSS pixels with 8px minimum spacing. | Phase 14 | CSS / Lighthouse Audit |
| **NFR-008** | Browser Camera Compatibility | HTML5 video stream must function across modern standards-compliant mobile/desktop browsers. | Chrome 110+, Safari 16+, Firefox 115+, Edge 110+. | Phase 15 | Cross-browser Testing |

---

### 3. Security Requirements Register (SEC)

| Req ID | Title | Description | Target Phase | Verification Method |
| :--- | :--- | :--- | :--- | :--- |
| **SEC-001** | PIN Storage Security | Employee PINs must be hashed using salted BCrypt (cost factor ≥ 12) or Argon2id. | Phase 4 | Cryptographic Audit |
| **SEC-002** | Rate Limiting & Lockout | Kiosk PIN attempts limited to 5 failures per 15 minutes per Employee ID to prevent brute force. | Phase 4 | Automated Attack Test |
| **SEC-003** | Admin Authentication | Admin dashboard requires strong password authentication producing signed JWT in HttpOnly cookie. | Phase 4 | Security Penetration Test |
| **SEC-004** | Private Photo Storage | Photos stored outside web server root; public directory browsing strictly disabled. | Phase 7 | Path Traversal / Direct URL Test |
| **SEC-005** | Image Upload Sanitization | Server must validate magic bytes (reject fake extensions) and strip EXIF metadata before saving. | Phase 7 | Malicious File Upload Test |
| **SEC-006** | File Size & Dimension Limit | Uploaded photos strictly capped at 2MB; resized/recompressed to ~200KB JPEG (max 1280x720). | Phase 7 | Boundary Unit Test |
| **SEC-007** | Immutable Audit Trail | Audit records cannot be updated or deleted via application APIs; append-only table structure. | Phase 10 | DB Constraint Test |
| **SEC-008** | Secret Sanitization | Passwords, PINs, JWT secrets, and DB credentials must never appear in application logs or DTOs. | Phase 4 | Log Inspection Test |

---

### 4. Data & Retention Requirements Register (DATA)

| Req ID | Title | Description | Target Phase | Verification Method |
| :--- | :--- | :--- | :--- | :--- |
| **DATA-001** | 3-Year Historical Retention | Attendance log records and audit logs must be retained for a minimum of 36 continuous months. | Phase 3 | Retention Policy Spec |
| **DATA-002** | Configurable Photo Retention | Photo retention duration is configurable (default: 90 days); older photos auto-purged from disk. | Phase 12 | Scheduled Worker Test |
| **DATA-003** | Foreign Key Deletion Safety | All relational foreign keys linking to `employees` must enforce `ON DELETE RESTRICT`. | Phase 3 | Schema Integrity Test |
| **DATA-004** | Timezone Uniformity | All database timestamps stored as `TIMESTAMPTZ` in UTC and formatted in `Asia/Kolkata`. | Phase 3, 8 | Integration Test |
