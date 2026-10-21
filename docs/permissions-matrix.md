# Role-Based Permissions & Access Control Matrix
## Staff Attendance Management System — Phase 2 Security Specification

**Document Version:** 1.0.0  
**Status:** Approved Security Baseline  

---

### 1. Defined System Actors

1. **`ANONYMOUS_KIOSK`:** Kiosk browser client in an idle state displaying the initial ID/PIN entry keypad. No employee identity is established. Possesses only a shared Kiosk Device Authorization Token.
2. **`EMPLOYEE_SESSION`:** An employee currently authenticated at the kiosk after successful Employee ID + PIN verification. Session is strictly ephemeral (expires after 60 seconds or immediately upon punch completion).
3. **`HR_ADMIN`:** Back-office administrator authorized to manage staff profiles, view attendance records, review photos, perform manual corrections, and export attendance reports.
4. **`SUPER_ADMIN`:** Senior administrator with complete system governance, including administrative user provisioning, company policy configuration (cut-off times, photo retention days), and kiosk pairing token generation.
5. **`SYSTEM_SCHEDULER`:** Internal Spring Boot background task runner executing cron maintenance (daily auto-closure and rolling photo retention purge).

---

### 2. Comprehensive Resource Access Matrix

| Resource & Operation | `ANONYMOUS_KIOSK` | `EMPLOYEE_SESSION` | `HR_ADMIN` | `SUPER_ADMIN` | `SYSTEM_SCHEDULER` |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Verify Employee ID & PIN** | ALLOW | DENY (Already active) | DENY | DENY | DENY |
| **Query Own Attendance State** | DENY | ALLOW (Own state only) | ALLOW | ALLOW | DENY |
| **Submit Check-In Punch** | DENY | ALLOW (Own session only)| DENY | DENY | DENY |
| **Submit Check-Out Punch** | DENY | ALLOW (Own session only)| DENY | DENY | DENY |
| **Upload Attendance Photo** | DENY | ALLOW (Attached to punch)| DENY | DENY | DENY |
| **Stream Full-Res Photo** | DENY | DENY | ALLOW | ALLOW | DENY |
| **Browse Employee Directory** | DENY | DENY | ALLOW | ALLOW | DENY |
| **Register New Employee** | DENY | DENY | ALLOW | ALLOW | DENY |
| **Update Employee Details** | DENY | DENY | ALLOW | ALLOW | DENY |
| **Deactivate / Reactivate Staff** | DENY | DENY | ALLOW | ALLOW | DENY |
| **Reset Employee PIN** | DENY | DENY | ALLOW | ALLOW | DENY |
| **View Daily Attendance Grid** | DENY | DENY | ALLOW | ALLOW | DENY |
| **View Historical Reports** | DENY | DENY | ALLOW | ALLOW | DENY |
| **Export Attendance CSV** | DENY | DENY | ALLOW | ALLOW | DENY |
| **Perform Manual Punch Adjustment** | DENY | DENY | ALLOW (With reason) | ALLOW (With reason) | DENY |
| **Auto-Close Incomplete Sessions**| DENY | DENY | DENY | DENY | ALLOW (04:00 AM job) |
| **Purge Expired Photos** | DENY | DENY | DENY | DENY | ALLOW (Nightly job) |
| **Inspect System Audit Logs** | DENY | DENY | ALLOW | ALLOW | DENY |
| **Generate Kiosk Pairing Key** | DENY | DENY | DENY | ALLOW | DENY |
| **Configure System Policies** | DENY | DENY | DENY | ALLOW | DENY |

---

### 3. Critical Security Boundaries
- **Zero Cross-Employee Visibility on Kiosk:** An employee at the kiosk can never query, infer, or view the attendance state, timestamps, or captured photos of any other employee.
- **Kiosk Memory Sanitization:** Upon punch completion, cancellation, or timeout, the kiosk frontend explicitly overwrites and clears all local variables containing employee tokens or names.
- **Photo Confidentiality:** Attendance photos are never exposed as static public URLs. Every photo retrieval request must present a valid Admin JWT; the backend verifies authorization before streaming the image bytes.
