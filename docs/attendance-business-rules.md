# Attendance Business Rules & Exception Matrix
## Staff Attendance Management System — Phase 2 Business Specification

**Document Version:** 1.0.0  
**Status:** Approved Business Rules Baseline  
**Primary Timezone:** `Asia/Kolkata` (UTC+05:30)

---

### 1. Master Attendance Business Rules (BR)

#### BR-001: Workday Definition & Cut-Off Boundary
- **Rule:** A logical "Workday" is determined by the company's configured cut-off time, provisionally established at **04:00 AM IST (`Asia/Kolkata`)**.
- **Explanation:** Any attendance punch recorded between 04:00:00 AM on Day $D$ and 03:59:59 AM on Day $D+1$ is assigned to the `work_date` of Day $D$.
- **Rationale:** Accommodates normal day schedules and employees working late into the night without falsely splitting their shift across two different calendar work-dates.

#### BR-002: Single Attendance Session Baseline
- **Rule:** In the initial production release, an employee is permitted exactly **one completed attendance session per work-date** (one Check-In and one Check-Out).
- **Extensibility Hook:** The relational schema includes `session_sequence_number` (default `1`) to facilitate future multi-session or split-shift support without breaking database constraints.

#### BR-003: Sequential Punch Requirement
- **Rule:** An employee cannot Check-Out without an active, prior Check-In session for the current workday.
- **Rule:** Check-Out timestamp ($T_{\text{out}}$) must strictly succeed Check-In timestamp ($T_{\text{in}}$): $T_{\text{out}} > T_{\text{in}}$.

#### BR-004: Mandatory Photo Evidence
- **Rule:** Every accepted Check-In and Check-Out must be accompanied by a valid, newly captured live photo image.
- **Rule:** Attendance records without a corresponding `photo_id` are prohibited by non-null database foreign keys.
- **Rule:** A photo serves exclusively as physical presence verification; it does not perform automated facial recognition.

#### BR-005: Automated Transition of Forgotten Checkouts
- **Rule:** If an employee checked in on Day $D$ but failed to check out, when the daily cut-off time (04:00 AM) of Day $D+1$ arrives, the system's scheduled maintenance worker transitions the session status from `INCOMPLETE` to `AUTO_CLOSED`.
- **Rule:** An `AUTO_CLOSED` session releases the employee's active session lock so they can freely check in for the new workday without administrative lock-out.
- **Rule:** The `AUTO_CLOSED` status remains visible on admin reports as an anomaly requiring review.

#### BR-006: Server-Authoritative Clock Invariant
- **Rule:** Device hardware clocks (tablets, laptops, phones) are untrusted. The official attendance timestamp is generated exclusively by the database engine (`CURRENT_TIMESTAMP`).

#### BR-007: Administrative Correction Immutability
- **Rule:** An administrator cannot delete an attendance record to erase history.
- **Rule:** An administrator may adjust timestamps or session status only by providing a non-empty `reason` string (minimum 10 characters).
- **Rule:** The system writes the pre-modification snapshot, post-modification snapshot, modifying admin ID, and timestamp into the append-only `audit_logs` table.

---

### 2. Comprehensive Exception Handling Matrix (21 Scenarios)

| # | Exception Condition | System Response & User Feedback | System Action / Data Invariant | Admin Action Required? |
| :--- | :--- | :--- | :--- | :---: |
| **EX-01** | **Employee checks in twice (double tap)** | Button disabled on first tap. Second request returns existing session confirmation. | Idempotency key deduplication; DB unique index prevents second row. | No |
| **EX-02** | **Network timeout after DB commit** | Kiosk displays "Checking status..."; re-queries backend upon reconnection. | Backend returns existing `CHECKED_IN` status; kiosk displays confirmation. | No |
| **EX-03** | **Employee attempts Checkout with no Check-In** | Kiosk displays: "No active check-in found for today. Please Check-In first." | Action rejected with HTTP 400 Bad Request; state unchanged. | No |
| **EX-04** | **Forgot checkout yesterday; checking in today** | Yesterday's session transitioned to `AUTO_CLOSED`; new Check-In proceeds smoothly. | Scheduled cut-off worker closed yesterday; today's Check-In creates new session. | Optional review |
| **EX-05** | **Employee deactivated while session open** | Kiosk blocks Checkout attempt: "Account is inactive. Contact Administrator." | Auth filter verifies `status == 'ACTIVE'`; open session flagged for admin review. | Yes (Manual close) |
| **EX-06** | **Repeated incorrect PIN (5 attempts)** | Kiosk displays: "Account locked due to repeated failed attempts. Try in 15 minutes." | Lockout timestamp written to employee record; brute-force alert logged. | No (Auto-unlocks) |
| **EX-07** | **Camera permission denied by browser** | Kiosk displays: "Camera access is required for attendance. Please enable camera permissions." | Punch rejected; camera initialization error logged; state unchanged. | No (Device setting) |
| **EX-08** | **Photo upload fails / payload truncated** | Kiosk displays: "Photo upload failed. Please try again." | Transaction rolled back; no session record created; zero orphan records. | No |
| **EX-09** | **Database down / connection pool full** | Kiosk displays: "Service temporarily unavailable. Please try again in a few moments." | Returns HTTP 503; error logged; zero partial or corrupted records. | Yes (Ops alert) |
| **EX-10** | **Kiosk browser refreshes mid-operation** | Browser reloads starting screen; all in-memory PIN state cleared. | Stateless REST design ensures backend integrity is untouched. | No |
| **EX-11** | **Device restarts or loses power** | Kiosk reboots to home screen. | Any uncommitted HTTP requests abort naturally via TCP disconnect. | No |
| **EX-12** | **Employee attempts 2nd check-in same day** | Kiosk displays: "You have already completed attendance for today." | Rejected by `uk_employee_work_date` unique constraint. | No |
| **EX-13** | **Two admins edit same record concurrently** | First edit succeeds; second edit receives: "Record modified by another administrator." | Optimistic locking checks `@Version`; second edit rejected with HTTP 409 Conflict. | Yes (Refresh & retry) |
| **EX-14** | **Admin corrects nonexistent record ID** | Admin UI displays: "Attendance record not found." | Returns HTTP 404 Not Found; error logged. | No |
| **EX-15** | **Employee has zero attendance history** | Admin reports display: "No attendance records found for this period." | Returns empty list `[]` with 200 OK; no null pointer exceptions. | No |
| **EX-16** | **Employee legally changes name** | Admin updates profile; employee ID remains constant. | Past records linked via immutable UUID foreign keys; history preserved. | No |
| **EX-17** | **Employee leaves and rejoins organization** | Admin reactivates existing profile; previous ID and historical logs retained. | `status` set to `ACTIVE`; new attendance appends to existing timeline. | Yes (Admin approval) |
| **EX-18** | **Public or company holiday** | Kiosk remains operational; punches recorded as normal with timestamp. | Absence is not automatically inferred without holiday calendar configuration. | Optional |
| **EX-19** | **Company updates working hours** | Workday cut-off rules apply to punches recorded after configuration update. | Existing historical records retain original recorded timestamps; no retroactive drift. | No |
| **EX-20** | **Corrupted or oversized image submitted** | Kiosk displays: "Invalid photo format. Maximum file size is 2MB." | Server magic-byte filter rejects file; HTTP 415 / 400 returned. | No |
| **EX-21** | **Punch recorded at 03:59:59 AM near cut-off** | Punch belongs to previous calendar day according to 04:00 AM rule. | Work-date calculation assigns date $D$; next punch at 04:00:01 AM assigns date $D+1$. | No |
