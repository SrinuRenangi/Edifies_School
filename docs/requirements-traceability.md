# Requirements Traceability Matrix (RTM)
## Staff Attendance Management System — Phase 2 Traceability Baseline

**Document Version:** 1.0.0  
**Status:** Approved Traceability Matrix  

---

### Traceability Mapping Table

| Business Objective | Requirement ID | Business Rule | Architectural Component | Planned Phase | Acceptance Scenario |
| :--- | :--- | :--- | :--- | :---: | :---: |
| **BO-1: Accurate Timestamps** | FR-008, DATA-004 | BR-001, BR-006 | `attendance-engine`, PostgreSQL | Phase 3, 8 | Scenario 1, 2, 11 |
| **BO-2: Mandatory Photo Evidence** | FR-004, FR-005, SEC-006 | BR-004 | `photo-storage`, `frontend-kiosk` | Phase 7, 15 | Scenario 1, 2, 7 |
| **BO-3: Employee ID & PIN Auth** | FR-001, FR-002, SEC-001 | BR-003, EX-06 | `auth-security`, BCrypt | Phase 4, 14 | Scenario 1, 5 |
| **BO-4: Admin Search & Review** | FR-013, FR-015, FR-016 | BR-005 | `reporting-audit`, `frontend-admin` | Phase 11, 16, 17| Scenario 15, 16 |
| **BO-5: Daily & Monthly Reports** | FR-015, FR-016, FR-020 | BR-001, EX-18 | `reporting-audit`, CSV Streamer | Phase 11, 18 | Scenario 15, 16 |
| **BO-6: Inactive Employee History** | FR-012, DATA-003 | BR-003, EX-05 | `employee-mgmt`, FK RESTRICT | Phase 5, 16 | Scenario 12, 16 |
| **BO-7: Duplicate & Concurrency Def**| FR-009, NFR-004 | BR-002, EX-01, EX-10 | `attendance-engine`, DB Constraints| Phase 8, 9, 19 | Scenario 3, 9, 10 |
| **BO-8: 3-Year Record Retention** | DATA-001, DATA-002 | BR-007 | PostgreSQL WAL, Cron Maintenance | Phase 3, 12, 20| Scenario 16 |
| **BO-9: Fast Touch Interface (<5s)** | NFR-001, NFR-007 | BR-001 | Angular Standalone Kiosk SPA | Phase 13, 14, 15| Scenario 1, 2 |
| **BO-10: Audited Corrections** | FR-018, FR-019, SEC-007 | BR-007, EX-13 | `reporting-audit`, `audit_logs` | Phase 10, 18 | Scenario 13, 15 |

---

### Verification Strategy by Component
1. **`auth-security`:** Verified via unit tests (PIN hashing), brute-force integration tests (Rate limiting), and JWT filter tests.
2. **`attendance-engine`:** Verified via concurrency tests (simultaneous punches), state transition unit tests, and cut-off boundary date calculator tests.
3. **`photo-storage`:** Verified via file format validation tests, path traversal security tests, and image resizing benchmarks.
4. **`reporting-audit`:** Verified via large-dataset SQL query plan inspection and RFC-4180 CSV export validation.
5. **`frontend-kiosk`:** Verified via browser automation (Playwright/Cypress) checking camera initialization, touch targets, and auto-reset timers.
