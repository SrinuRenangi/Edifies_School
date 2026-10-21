# Architecture Quality Attributes & Verification Plan
## Staff Attendance Management System — Phase 3 Technical Specification

**Document Version:** 1.0.0  
**Status:** Approved Quality Attributes  

---

### Quality Attributes Register

| Quality Attribute | Desired Architectural Outcome | Responsible Component | Verification Method | Acceptance Evidence Required |
| :--- | :--- | :--- | :--- | :--- |
| **QA-1: Attendance Data Correctness** | Zero duplicate sessions, zero missing checkouts disguised as present, zero false success confirmations. | `attendance-processing`, PostgreSQL constraints | Concurrency unit & chaos tests (Phase 19) | 0 duplicate rows under 50 simultaneous identical requests; partial unique index throws 409. |
| **QA-2: Security & Privacy** | Employee PINs and admin passwords unrecoverable; zero public photo exposure; brute-force locked out. | `auth-security`, `photo-storage` | Automated penetration & OWASP ZAP scan | BCrypt salted hashes verified; direct URL photo access returns 401; 6th PIN attempt returns 429. |
| **QA-3: Maintainability** | Clean package isolation; zero circular dependencies; replaceable photo storage provider. | Modular monolith design, ArchUnit rules | ArchUnit static analysis test suite | CI build fails if circular dependency is detected or `photo-storage` imports `attendance`. |
| **QA-4: Testability** | All business rules testable without launching a physical browser camera or external network. | Interface abstractions, Mockito, Testcontainers | Automated unit & integration test suite | > 85% branch coverage on domain state machine and workday cut-off calculations. |
| **QA-5: Reliability During Failures** | Zero corrupted or partial data if network, database, or camera crashes mid-punch. | Spring `@Transactional`, TransactionSynchronization | Network kill simulation & connection pool exhaustion tests | Zero orphan DB rows when image fails; zero orphan images when DB fails. |
| **QA-6: Performance** | Kiosk punch roundtrip $\le$ 3.0s; single-day queries $\le$ 500ms; monthly report $\le$ 2.0s. | Indexed PostgreSQL queries, client canvas compression | JMeter / Gatling performance benchmarks | P99 latency charts meet targets under 10 concurrent kiosk requests. |
| **QA-7: Accessibility & Responsiveness** | Flawless UI rendering across 360px to 4K viewports; touch targets $\ge$ 48x48px. | Angular Standalone UI, SCSS design tokens | Playwright visual regression + Lighthouse audit | Lighthouse Accessibility score $\ge$ 95; zero layout clipping across mobile/tablet. |
| **QA-8: Data Retention** | 3-year historical text attendance and audit logs permanently accessible; ex-employees retained. | PostgreSQL `ON DELETE RESTRICT`, Flyway migrations | Historical query integration test on ex-employees | 3-year date range query executes successfully; deactivation leaves records 100% intact. |
| **QA-9: Recoverability** | RPO $\le$ 24 hours; RTO $\le$ 1 hour upon complete host or database destruction. | Automated `backup.sh` & `restore.sh` scripts | Automated restoration drill in disposable container | `pg_restore` completes without errors; row counts match source database. |
| **QA-10: Operational Simplicity** | Deployed via single `docker compose up -d` command on any commodity Linux/Windows server with Docker. | Docker Compose, Multi-stage Dockerfiles | Clean installation drill on fresh environment | System running with green health checks in under 5 minutes from repository clone. |
