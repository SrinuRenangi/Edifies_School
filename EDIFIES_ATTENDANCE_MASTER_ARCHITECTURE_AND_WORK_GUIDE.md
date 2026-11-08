# EDIFIES SCHOOL ATTENDANCE SYSTEM
# MASTER ARCHITECTURE, PROJECT STATUS & AI WORKING GUIDE

**Document type:** Living project source of truth  
**Project:** Edifies School Staff Attendance Management System  
**Project root:** `C:\Users\sriva\OneDrive\Desktop\Edifies_School`  
**PHP application:** `C:\Users\sriva\OneDrive\Desktop\Edifies_School\php-attendance`  
**Face-research lab:** `C:\Users\sriva\OneDrive\Desktop\Edifies_Face_Recognition_Lab`  
**Document status:** Baseline Established — Completed through first read-only audit (2026-11-05)  
**Owner:** Project Owner (user)  

> [!IMPORTANT]
> This is the **one living master document** for the project. Before doing any work, Antigravity must read this file. After each owner-approved task, update the relevant status and decision entries here. Do not create a new collection of stage documents unless the Project Owner explicitly asks for them.

---

## 0. PROJECT MAP, APPLICATION OWNERSHIP & WORKSPACE BOUNDARIES

### 0.1 Complete Directory Structure & Ownership Map

The physical filesystem on disk contains three distinct environments across two root folders. Source inspection confirms their exact identities:

```
C:\Users\sriva\OneDrive\Desktop\
│
├── Edifies_School/                                     # GIT REPOSITORY ROOT (Holds Legacy Java & Nested PHP)
│   ├── .git/                                           # Git metadata for Edifies_School (Tracks Java stack only)
│   │
│   ├── php-attendance/                                 # [ACTIVE PRODUCT] — EXCLUSIVE ACTIVE DEVELOPMENT TARGET
│   │   ├── app/ (Config, Controllers, Models, Views)   # CodeIgniter 4.7.6 MVC application backend
│   │   ├── public/ (index.php, sw.js, css, js)         # Public web root, PWA Service Worker & Kiosk UI
│   │   ├── tests/ (unit, feature, database)            # 103 PHPUnit 10.5.66 automated regression tests
│   │   ├── writable/                                   # Local session, debugbar, and cache directory
│   │   ├── composer.json, composer.lock                # Locked dependencies (CI4 v4.7.6, PHP 8.2+)
│   │   ├── .env                                        # Environment configuration (MariaDB port 3307)
│   │   └── spark                                       # CodeIgniter CLI tool
│   │
│   ├── [PRESERVED LEGACY JAVA APPLICATION]             # FROZEN REFERENCE ONLY — DO NOT DEVELOP NEW FEATURES
│   │   ├── pom.xml                                     # Spring Boot 3.3.4 / Java 21 build descriptor
│   │   ├── src/main/java/com/edifies/attendance/       # Legacy Spring Boot backend classes
│   │   ├── src/main/resources/application.yml          # PostgreSQL datasource config (port 5435)
│   │   ├── frontend/                                   # Legacy Angular frontend application
│   │   ├── pgdata/                                     # Local PostgreSQL data files
│   │   ├── photos/, photos_recovery/                   # Legacy Java photo upload directories
│   │   ├── backups/                                    # Legacy disaster recovery archive bundles
│   │   ├── scripts/backup/ (backup.ps1)                # PostgreSQL backup and recovery scripts
│   │   ├── scripts/seeds/ (*.sql)                      # PL/pgSQL database seed scripts
│   │   ├── run_app.ps1                                 # [TRAP] Startup script for Postgres 5435 + Spring Boot 8080
│   │   └── mvnw, mvnw.cmd                              # Maven wrapper executables
│   │
│   └── EDIFIES_ATTENDANCE_MASTER_ARCHITECTURE_AND_WORK_GUIDE.md  # Single Master Document (This file)
│
└── Edifies_Face_Recognition_Lab/                       # [OPTIONAL RESEARCH LAB] — ISOLATED NON-PRODUCTION
    ├── models/ (yunet, sface, ort-wasm)                # Acquired ONNX model binaries and WASM runtime
    ├── prototype/ (index.html, app.js, serve.py)       # Browser test harness (port 8090; zero DB connection)
    ├── test-datasets/                                  # Synthetic non-biometric test video (WebM)
    └── reports/                                        # Stage 3.4 research logs and governance matrices
```

### 0.2 Application Ownership & Operating Matrix

| Property | Active Attendance Product | Preserved Legacy Application | Isolated Research Lab |
| :--- | :--- | :--- | :--- |
| **Directory** | `Edifies_School/php-attendance/` | `Edifies_School/` (root files) | `Edifies_Face_Recognition_Lab/` |
| **Role & Purpose** | **Primary Attendance Application** | Historical reference archive | Optional offline biometric feasibility study |
| **Backend Runtime** | **PHP 8.5.12 (CLI / Built-in Server)** | Java 21 / Spring Boot 3.3.4 | Python 3.12 (HTTP static server `serve.py`) |
| **Framework** | **CodeIgniter v4.7.6** | Spring Boot 3.3.4 + JPA/Hibernate | None (Vanilla JS + ONNX Runtime Web 1.18.0) |
| **Authoritative DB**| **MariaDB 11.4 LTS (Port 3307)** | PostgreSQL 17/18 (Port 5435) | **NONE** (Zero database connection) |
| **Frontend UI** | Server-rendered HTML + CSS + Vanilla JS | Angular (`frontend/` directory) | Single-page HTML canvas harness (`index.html`) |
| **Kiosk Support** | Browser / PWA (`public/sw.js`, tablet UI) | Legacy Web interface | Synthetic video replay canvas harness |
| **Active Port** | Port 8088 (`php -S 127.0.0.1:8088`) | Port 8080 (`run_app.ps1` default) | Port 8090 (`serve.py` default) |
| **Git Tracking** | **UNTRACKED (`?? php-attendance/`)** | Fully tracked in root `.git` | Unversioned independent workspace |
| **Development Status** | **ACTIVE FORWARD DEVELOPMENT** | **FROZEN / PRESERVED ONLY** | **ISOLATED EXPERIMENT ONLY** |

### 0.3 Git Repository Boundaries & Critical Safety Warnings

> [!CAUTION]
> **CRITICAL REPOSITORY VULNERABILITY: PHP APPLICATION IS UNTRACKED IN ROOT GIT**  
> Source inspection reveals:
> 1. `C:\Users\sriva\OneDrive\Desktop\Edifies_School` contains the only `.git` directory.
> 2. That Git repository was initialized for the legacy Java/Spring Boot project.
> 3. `php-attendance/` has **NO** `.git` directory of its own, is **NOT** listed in `.gitignore`, and is currently **UNTRACKED** by the root repository (`?? php-attendance/`).
> 4. **DANGER:** Running `git clean -fd` or destructive git reset commands in `Edifies_School` will **PERMANENTLY DESTROY** the entire active PHP attendance application!
> 5. **ABSOLUTE RULE:** Never run `git clean` or destructive Git reset commands in `Edifies_School`.

### 0.4 Database Ownership & Zero Cross-Dependency Verification

Comprehensive source inspection across all codebases confirms:
- **Zero Cross-Application Dependencies:**
  - `php-attendance/` contains zero references to PostgreSQL, port 5435, Spring Boot, Java, or `pom.xml`.
  - `src/main/java/` contains zero references to MariaDB, port 3307, PHP, or CodeIgniter.
  - `Edifies_Face_Recognition_Lab` contains zero database credentials or query code.
- **Independent Execution Confirmed:**
  - The PHP application runs completely independently using PHP 8.5.12 and MariaDB. It does not require Java, Maven, or PostgreSQL to be installed or running.
  - The Java application is completely independent. It does not require PHP or MariaDB.
- **The Startup Script Trap:**
  - The root script [`run_app.ps1`](file:///C:/Users/sriva/OneDrive/Desktop/Edifies_School/run_app.ps1) starts PostgreSQL 18 and launches Spring Boot. **It launches the legacy application, NOT the PHP application.** Never use `run_app.ps1` to run the attendance product.

### 0.5 Verified Startup & Test Commands

#### A. Active Product (PHP Attendance):
```powershell
# 1. To start the active attendance application web server:
Set-Location "C:\Users\sriva\OneDrive\Desktop\Edifies_School\php-attendance"
& "C:\tools\php85\php.exe" -S 127.0.0.1:8088 -t public

# 2. To run the complete automated test suite (103 tests, 365 assertions):
Set-Location "C:\Users\sriva\OneDrive\Desktop\Edifies_School\php-attendance"
& "C:\tools\php85\php.exe" vendor/bin/phpunit tests/unit tests/feature
```

#### B. Isolated Research Lab (Face Feasibility):
```powershell
# To run isolated lab harness (research only):
Set-Location "C:\Users\sriva\OneDrive\Desktop\Edifies_Face_Recognition_Lab\prototype"
python serve.py
```

### 0.6 Explicit Working Rules & Boundaries

These rules are permanent and mandatory for all future AI and developer interactions:

#### ACTIVE APPLICATION
Only the PHP CodeIgniter application under `C:\Users\sriva\OneDrive\Desktop\Edifies_School\php-attendance` is the forward development target. All new product features, bug fixes, UI enhancements, and kiosk workflows must be implemented exclusively within this directory.

#### LEGACY APPLICATION
The Java/Spring Boot application in `C:\Users\sriva\OneDrive\Desktop\Edifies_School` (root `pom.xml`, `src/`, `frontend/`, `scripts/`) is preserved strictly for historical reference. Do not implement new product features, migrations, or bug fixes in the Java application. Do not start PostgreSQL or Spring Boot unless explicitly directed to inspect a legacy reference implementation.

#### RESEARCH APPLICATION
The face-recognition laboratory in `C:\Users\sriva\OneDrive\Desktop\Edifies_Face_Recognition_Lab` is an independent, offline experimental sandbox. It must never be treated as the production attendance application or connected to production databases.

#### FILE MODIFICATION BOUNDARY
Every future task must identify the exact target application and permitted directory before modifying any files:
- **Never modify the parent directory (`Edifies_School`) indiscriminately.**
- **Never edit Java files (`src/`, `pom.xml`) while carrying out attendance product tasks.**
- **Never edit PHP files (`php-attendance/`) while carrying out isolated face-research tasks.**
- **Always verify the active working directory before creating files or running commands.**

### 0.7 Safest Proposed Folder Arrangement (For Owner Approval)

To eliminate confusion without disrupting ongoing work:
1. **Immediate Recommendation (Active Now):** Set the IDE / AI active workspace directly to `C:\Users\sriva\OneDrive\Desktop\Edifies_School\php-attendance`. This isolates the editor from the root Java files and prevents accidental edits.
2. **Permanent Structural Clean-up (Awaiting Owner Authorization):**
   - Option A: Move the legacy Java files into a dedicated subfolder (e.g. `Edifies_School/legacy-java-app/`) so the root does not confuse developers.
   - Option B: Initialize a dedicated Git repository inside `php-attendance/` (`git init`) and commit the active codebase, eliminating the risk of untracked deletion.

---

## 1. PROJECT PURPOSE — DO NOT LOSE SIGHT OF THIS

Build a simple, affordable, reliable staff attendance application for Edifies School.

The application is not an enterprise platform, security product, AI platform or research product. It is an attendance system. The face-recognition capability is an optional enhancement being investigated to make the kiosk easier to use.

### Real-world operating arrangement
- **Admin:** Uses a separate laptop/desktop to manage the application. The admin's computer is not the attendance kiosk.
- **Manager:** Has restricted access to the Staff Directory and Attendance Kiosk areas only, subject to the existing approved role rules.
- **Kiosk:** A separate, inexpensive Android phone or tablet placed at the attendance point. Staff use this device to record attendance.
- **Scale:** Approximately 20 staff members and roughly three years of attendance history.
- **Hosting:** Prefer affordable shared hosting/cPanel or a small, inexpensive server. Deployment must be straightforward and maintainable by a small team.
- **Primary objective:** Correct attendance, easy administration, reliable photo handling, and a kiosk that works consistently.

### Product experience
The intended kiosk experience is:
1. Staff member approaches the kiosk.
2. The kiosk captures the required attendance photo and/or performs an approved identification method.
3. The system records the correct employee and attendance event.
4. The kiosk clearly confirms success or explains what the person must do next.
5. Admin/Manager can inspect attendance from their permitted screens.

Face recognition is not allowed to guess whether someone is entering or leaving. Unless separately approved directional hardware exists, the kiosk must operate in an explicit `ENTRY_ONLY` or `EXIT_ONLY` mode. Never recreate a time-based automatic checkout heuristic.

---

## 2. PROJECT DECISIONS THAT MUST BE PRESERVED

These decisions came from the Project Owner's repeated requirements. Do not reverse them without asking.
1. **Cost and simplicity come first.** Do not add technology merely because it is fashionable or architecturally impressive.
2. **Keep the stack small.** Prefer built-in framework features and ordinary PHP/JavaScript over extra services and frameworks.
3. **Preserve the existing project structure.** Inspect first. Do not rewrite an existing working module just to impose a preferred pattern.
4. **Do one task at a time.** Do not begin the next task or stage until the owner reviews the current result and explicitly authorizes continuation.
5. **No unrequested documentation flood.** This master file is the central project document. Do not create many separate Markdown reports for routine work.
6. **Do not claim work is complete without evidence.** Code existing, tests passing, and real-world behavior are different levels of proof.
7. **Responsive design is mandatory.** Every touched screen must be checked on phone, tablet, small/medium laptop and large laptop/desktop widths.
8. **Admin and kiosk devices are separate.** Never design the system around the admin laptop acting as the kiosk.
9. **Keep the legacy Java/Spring Boot/PostgreSQL application preserved.** The PHP application is the chosen forward direction. Do not delete, migrate over, or casually modify the Java application or its database.
10. **No unsafe attendance automation.** Do not infer ENTRY/EXIT from elapsed time or facial recognition alone.
11. **No uncontrolled spending or paid AI/cloud dependency.** Any recurring or per-use cost requires explicit owner approval.
12. **Face recognition is optional and conditional.** The attendance application must have a usable non-face fallback (such as the approved existing/manual method, PIN or QR) if face recognition is unavailable, inaccurate, too slow or rejected.

---

## 3. SELECTED TECHNOLOGY DIRECTION

This is the selected direction, not permission to upgrade every dependency. The first audit must inspect the actual lock files and installed versions.

| Area | Selected direction | Rule |
| :--- | :--- | :--- |
| **Backend** | PHP 8.5 | Keep PHP as the production backend. |
| **Framework** | CodeIgniter 4 (reported project version: 4.7.6) | Verify composer.json, lock file and installed code; do not assume. |
| **Database** | MariaDB 11.4 LTS | Keep one relational database for the PHP application. Verify actual connection settings without exposing secrets. |
| **Frontend** | Server-rendered HTML, CSS and Vanilla JavaScript | No React/Angular rewrite. |
| **Kiosk** | Android browser/PWA | Do not require a separate native Android app unless a real limitation proves it necessary. |
| **Offline support** | Service Worker for static shell; small local attendance queue if required | Never cache authenticated API responses, admin pages, credentials or personal/biometric content in the static cache. |
| **Face inference research** | ONNX Runtime Web 1.18.0 + WASM, YuNet and SFace candidates | Research only until the real-device and data gates pass. |
| **Deployment** | Shared hosting/cPanel or small Ubuntu server | Verify host capabilities before choosing deployment details. |

### Explicitly avoid unless the owner approves a demonstrated need
- Microservices
- Kubernetes
- Docker as a production requirement
- Redis
- Kafka or message brokers
- Node.js production backend
- React or Angular frontend
- Paid cloud face-recognition APIs
- GPU servers or cloud inference
- Multi-tenant organization/workspace architecture
- Complex repository/adapter abstractions that add no practical value
- Custom cryptographic protocols

Build a modular monolith using the framework's ordinary MVC conventions. A few focused service classes are acceptable where business logic needs transaction or workflow coordination. Do not build a framework inside the framework.

---

## 4. SYSTEM CONTEXT AND DEPLOYMENT SHAPE

Keep the deployment as close as possible to this simple arrangement:

```
             Admin laptop / Manager browser
                         |
                         | HTTPS
                         v
          +--------------------------------+
          | One CodeIgniter PHP application|
          |                                |
          | Admin screens                  |
          | Manager screens                |
          | Kiosk screens / JSON endpoints |
          | Attendance business logic     |
          | Authentication / permissions  |
          +---------------+----------------+
                          |
                          v
                 MariaDB database
                          |
                          v
             Private photo file storage

       Separate Android phone/tablet kiosk
                         |
                         +------ HTTPS ------> Same PHP application

       Separate isolated face-research lab
       (not a production service; no DB connection)
```

There should be one deployable PHP application and one MariaDB database for the attendance product. Admin, Manager and Kiosk are different user experiences/roles within that application, not separate services.

The face-research lab is separate from production. It must not be treated as a second production backend.

---

## 5. APPLICATION ARCHITECTURE — SIMPLE LAYERS

Use these practical layers. Do not introduce additional layers without a concrete reason.

### Layer 1 — Presentation
Contains:
- Admin web screens.
- Manager-restricted screens.
- Kiosk screen optimized for touch and camera use.
- HTML templates/views, CSS and Vanilla JavaScript.
- Small browser-side helpers for camera preview, photo capture and offline queue interaction.

Responsibilities:
- Display information and collect user input.
- Basic client-side validation for usability.
- Show clear loading, success, failure and offline states.
- Never decide authorization or attendance correctness by itself.

### Layer 2 — HTTP Controllers / Request Handling
Use ordinary CodeIgniter controllers and filters.

Responsibilities:
- Authenticate/authorize the request through framework services and filters.
- Validate request input and file uploads.
- Call a focused application service or model operation.
- Return an HTML page or a small JSON response.

Controllers must remain thin. Do not put multi-step attendance transactions or role rules into large controller methods.

### Layer 3 — Application Services / Business Rules
Use focused PHP service classes only where useful, for example:
- Attendance recording and synchronization.
- Device pairing/revocation.
- Photo validation/storage coordination.
- Reporting calculations.

Responsibilities:
- Enforce attendance rules.
- Coordinate database transactions.
- Handle idempotency and duplicate submissions.
- Enforce valid ENTRY/EXIT transitions.
- Return clear success/error results.

Do not create interfaces and abstractions for every class. A service should exist because it contains real reusable workflow logic, not to satisfy an architectural diagram.

### Layer 4 — Persistence and Local Infrastructure
Use CodeIgniter Models/database facilities and a small number of focused helpers for:
- MariaDB access.
- Private filesystem photo storage.
- Session/authentication services.
- Logging and audit records.
- Service Worker/static assets.

Use parameterized queries/query builder. Keep schema changes in versioned migrations. Do not create a second database or a separate database service.

---

## 6. FUNCTIONAL MODULES AND REQUIRED BEHAVIOR

The first audit must identify which of these modules already exist, are partial, or are missing. This table is the intended product scope, not a claim that every module is implemented.

### 6.1 Authentication and roles
- Login/logout.
- Secure password hashing using supported PHP/framework APIs.
- Admin role: full authorized administration.
- Manager role: only Staff Directory and Attendance Kiosk areas should be available, as specified by the owner.
- Enforce permissions on the server for every protected route/action. Hiding a menu item is not authorization.
- Basic login throttling/lockout and session protections.
- Never hardcode production credentials, PINs or device tokens.

### 6.2 Staff Directory
- Add, view, edit and deactivate employees (use the existing approved deletion policy; do not destroy attendance history).
- Employee ID must be unique.
- Search by employee name or Employee ID.
- Clear active/inactive status.
- Validate required fields and avoid duplicate employees.

### 6.3 Kiosk and device management
- Kiosk runs on a separate Android phone/tablet.
- Device pairing must use a deliberate admin-controlled process.
- Each device has its own revocable credential/token; never use a shared hardcoded fallback token.
- Show device status and last successful synchronization where available.
- Provide a revoke action and preserve an audit trail.
- Kiosk is configured for ENTRY_ONLY or EXIT_ONLY; mode must be visible and understandable to the operator.

### 6.4 Attendance
- Record employee, event type, event timestamp, source device and any required photo reference.
- Prevent duplicate submission/replay.
- Validate the current attendance state before accepting an ENTRY or EXIT.
- Use one database transaction for the complete attendance operation.
- Serialize concurrent operations for the same employee using a verified locking/constraint strategy.
- Server receipt time is authoritative. Client time can be retained only as diagnostic metadata and must be labelled untrusted.
- No automatic checkout after five minutes or any other arbitrary elapsed-time rule.
- Corrections must be permission-controlled and auditable.

### 6.5 Attendance history and reports
- Day-wise and month-wise views.
- Search by name and Employee ID.
- Clear in-time/out-time and missing/irregular event display.
- Export only when an owner-approved format is implemented.
- Do not calculate or display a metric unless its definition is clear and tested.

### 6.6 Photo capture and storage
- Capture only when required by the approved attendance workflow.
- Camera access must be foreground, user-visible and limited to the kiosk page/session.
- Validate MIME type, actual file signature where practical, file size and dimensions.
- Generate server-side random filenames; do not trust client filenames.
- Store photos outside the public web root or behind an authorized download controller.
- Never expose a direct public path to private attendance photos.
- Define retention and deletion behavior with the owner.
- Do not store face crops/embeddings as a side effect of ordinary photo capture.

### 6.7 Offline operation and synchronization
Offline support should be simple and bounded:
- Cache the static application shell only.
- Keep a small local queue for attendance attempts only if required by the product.
- Give every queued event a unique idempotency/client UUID.
- Synchronize when connectivity returns.
- Server validates the device, employee, kiosk mode, attendance transition and duplicate ID before committing.
- Do not trust the device clock as the authoritative attendance time.
- Do not store credentials or biometric templates in plaintext browser storage.
- Do not cache admin responses, private photos, API responses or credentials in the Service Worker cache.
- Show pending/synced/failed state clearly. Never silently discard a punch.

If offline photo capture cannot be made reliable and safe on the target host/device, document the limitation and ask the owner to choose the simpler operational fallback rather than building an elaborate queue.

### 6.8 Audit and operational logs
Record only useful security/administrative events:
- Login failures/lockout events.
- Device pair/revoke events.
- Attendance corrections.
- Important admin changes.
- Synchronization failures where useful.

Do not log passwords, PINs, raw tokens, face images, embeddings or full secrets.

---

## 7. DATA ARCHITECTURE — KEEP IT SMALL

The expected data volume (about 20 staff and three years) does not justify distributed storage or a large analytics stack. MariaDB is sufficient unless measured evidence proves otherwise.

The reports describe an event-ledger plus daily aggregate design:
- `employees`
- `attendance_events` (append-only event history)
- `attendance_records` (daily/current summary)
- `kiosk_devices`
- login/audit tables
- optional face-template storage (not approved for production)

These names and their exact columns/constraints must be confirmed against actual migrations and schema. Do not create duplicate tables because this document lists conceptual responsibilities.

### Data rules
- Employee ID unique.
- Attendance event idempotency key unique.
- Attendance state changes transactional.
- Keep historical attendance when an employee becomes inactive.
- Use foreign keys and indexes where justified by actual queries.
- Store timestamps consistently; show them in the institution's local time zone (Asia/Kolkata).
- Keep photo files separate from relational attendance rows; store a private path/reference and metadata.
- Backups must cover both the database and required private photo files. Test restoration, not just backup creation.

Do not add organization/workspace multi-tenancy for a single-school installation unless the owner changes the business scope.

---

## 8. OPTIONAL FACE-RECOGNITION COMPONENT — DO NOT LET IT CONTROL THE PROJECT

Face recognition is an optional enhancement, not a prerequisite for completing the basic attendance system.

### Current research candidates (reported, not production-approved)
- **YuNet INT8:** face detection and five-point landmark output; browser input reported as `[1,3,640,640]` float32 BGR.
- **SFace INT8:** face embedding generation; browser input reported as `[1,3,112,112]` float32 RGB and 128-dimensional output.
- **ONNX Runtime Web 1.18.0** with WASM/SIMD.
- **MiniFASNet:** quarantined; do not acquire/use without a separate owner-approved decision.

Actual model files, hashes, licenses and graph metadata must be inspected before relying on these values.

### Simple feasibility sequence
1. First prove the current browser runtime works (reported in Stage 3.4.2.1).
2. Obtain an appropriate, owner-approved and legally reviewed test-data path before using real faces.
3. Run a small, controlled feasibility pilot on consenting adults only, if approved. Do not involve students or incidental bystanders.
4. Test YuNet detection under realistic kiosk distance, light and pose conditions.
5. Only if detection is usable, test SFace alignment and recognition separately.
6. Test on an actual inexpensive Android device before deciding whether browser-local inference is viable.
7. Integrate only if accuracy, speed, privacy, operating cost and licensing are acceptable.

Do not turn this into a large academic research project. The pilot should answer a practical decision: does this approach work adequately on our actual low-cost kiosk device and operating conditions?

### Recognition safety rules
- Face recognition identifies a person; it does not determine ENTRY versus EXIT.
- Require top-match confidence and an ambiguity/unknown-person rejection strategy if 1:N recognition is later approved.
- Do not use OpenCV's 0.363 reference threshold as an attendance threshold.
- Do not claim liveness/spoof resistance without an actually validated PAD method.
- Never silently record attendance for an uncertain identity.
- Always keep the approved non-face fallback available.
- Do not enroll staff or persist face templates until the owner separately approves the purpose, participant process, retention, storage and deletion behavior.
- No model currently has production clearance established by the supplied reports.

---

## 9. PRACTICAL SECURITY BASELINE — SECURE, NOT OVERENGINEERED

Implement and verify the following proportionate protections:
1. HTTPS in deployment.
2. Secure password hashing using PHP's supported password APIs/framework authentication.
3. Server-side RBAC on every protected route and action.
4. CSRF protection for browser form/state-changing requests.
5. Parameterized database access.
6. Input validation and safe output escaping.
7. Upload type/size/content validation and private photo storage.
8. Per-device credentials with controlled pairing and revocation.
9. Basic rate limiting/login lockout.
10. Session cookies with appropriate Secure, HttpOnly and SameSite settings in production.
11. Secrets in environment/hosting configuration, never committed or displayed.
12. Database and photo backups, with a documented restore test.
13. Minimal audit logs for sensitive actions.

Do not add bespoke encryption frameworks, token-rotation systems, multi-factor platforms, external SIEM tools or complex origin/security layers unless a specific, demonstrated threat or deployment requirement warrants them. Use framework and hosting capabilities first.

No security mechanism can compensate for poor access control, insecure file storage or missing backups.

---

## 10. UI AND RESPONSIVENESS STANDARD

Every module must be clean, readable and easy to operate. The kiosk must be touch-first; admin screens must be efficient rather than decorative.

For every module changed, check all four layout classes:

| Device class | Minimum validation target |
| :--- | :--- |
| **Phone** | 360–430 CSS px; portrait and landscape where relevant |
| **Tablet/kiosk** | 600–900 CSS px; touch targets and camera preview |
| **Small/medium laptop** | 1024–1366 CSS px |
| **Large laptop/desktop** | 1440 CSS px and above |

### Review checklist:
- Alignment, spacing, padding and typography.
- Tables and forms at narrow widths.
- Touch target sizes and readable kiosk status.
- Loading, empty, validation and error states.
- Photo/video preview sizing.
- No horizontal overflow unless intentionally contained.
- Consistent navigation and role-appropriate menus.

Do not redesign unrelated screens when changing one module. Preserve the existing visual language unless the owner asks for a redesign.

---

## 11. TESTING AND ACCEPTANCE STANDARD

A feature is complete only when its behavior is demonstrated, not merely when code has been written.

### Required checks for each module:
1. **Functionality:** normal path, invalid input, edge cases and permission boundaries.
2. **Data correctness:** saved values, duplicate prevention, history and transactions.
3. **Security:** unauthorized access, invalid device, tampered input and upload handling as relevant.
4. **UI:** alignment, styles, clear feedback and no broken navigation.
5. **Responsiveness:** phone, tablet, small/medium laptop and large laptop.
6. **Regression:** existing modules still work.
7. **Operations:** useful logs, error handling and recovery behavior.

Use the actual test suite and report exact commands and results. Do not claim manual testing when only unit tests ran. Do not claim physical-device testing when only desktop emulation ran.

For any DB-backed tests, establish whether the test mutates data before running it. Never run destructive tests against a real or production database. Use a clearly identified disposable test database and backup only after owner authorization.

---

## 12. DEPLOYMENT TARGET

Deployment should remain low-cost and simple:
- One PHP application.
- One MariaDB database.
- Private file storage for attendance photos.
- HTTPS.
- A standard web server/hosting platform.
- No permanently running Node service.
- No Redis, Kafka, Kubernetes or GPU requirement.

Before selecting a hosting provider or plan, verify:
- Supported PHP version and required extensions.
- CodeIgniter compatibility.
- MariaDB/MySQL version and connection limits.
- Storage quota and backup options.
- Cron availability if actually required.
- HTTPS and certificate support.
- Maximum upload size and request execution limits.
- Ability to keep writable/private files outside the public web root.

Do not buy hosting, domains, devices or paid services without explicit owner approval.

---

## 13. PROJECT STATUS — EVIDENCE-BACKED AUDIT LEDGER

*(Updated by Antigravity on 2026-11-05 following the Baseline Audit in Section 16)*

### 13.1 Attendance application

| Area | Conversation/report history | Audit Status (2026-11-05) | Audit Evidence Summary |
| :--- | :--- | :--- | :--- |
| **PHP/CodeIgniter/MariaDB direction** | Selected to reduce cost and deployment complexity | **VERIFIED BY SOURCE INSPECTION** | PHP 8.5.12 CLI runtime verified; CodeIgniter `v4.7.6` confirmed in `composer.lock`; MariaDB connection configured to port 3307 in `php-attendance/.env`. |
| **Employee directory** | Required product module | **VERIFIED BY SOURCE INSPECTION** | `Admin\EmployeeController.php` and `EmployeeModel.php` provide full CRUD, search, department/status filtering, pagination, soft deactivation, and unique `employee_id` validation. |
| **Admin authentication** | Required | **VERIFIED BY SOURCE INSPECTION** | `Admin\AuthController.php`, `AdminUserModel.php`, and `AdminAuthFilter.php` implement bcrypt hashing, login lockout throttling (`RateLimitFilter`), and session management. |
| **Manager restricted access** | Admin full access; Manager limited to Staff Directory and Kiosk | **VERIFIED BY EXECUTED TEST** | Resolved in Task 1. Server-side role enforcement verified: `AdminAuthFilter.php` returns HTTP 403 (error_403 HTML view or JSON); `Routes.php` restricts `/admin`, `/admin/dashboard`, `/admin/face-lab`, `/face-lab`, `/api/admin/stats`, and employee mutations to `ADMIN,ROLE_ADMIN`; `AuthController.php` routes Manager login to `/admin/employees`; `DashboardController`, `FaceLabController`, `KioskDeviceController`, and `AttendanceReviewController` enforce controller defense-in-depth; UI hides Dashboard button and re-targets brand link for Managers. 14/14 automated feature assertions passed. |
| **Kiosk UI** | Separate Android phone/tablet | **VERIFIED BY EXECUTED TEST** | Resolved in Task 2. Implemented touch-friendly 6-digit numeric keypad on `/attendance`, visual dot progress indicators, two-step verification & confirmation flow, and single-use verification tokens. Server-authoritative terminal mode (`ENTRY_ONLY` / `EXIT_ONLY`) enforced via canonical `AttendanceSyncService`. 15/15 automated tests passed in `EmployeePinAttendanceTest.php`. |
| **Attendance ENTRY/EXIT** | Explicit ENTRY_ONLY / EXIT_ONLY; no time heuristic | **VERIFIED BY SOURCE INSPECTION** | `AttendanceSyncService.php` enforces terminal modes `ENTRY_ONLY` and `EXIT_ONLY`. Time-based checkout heuristics are permanently removed. |
| **Event ledger/idempotency/concurrency** | Append-only events, idempotency and locking reported | **VERIFIED BY SOURCE INSPECTION** | `attendance_events` ledger and `attendance_records` summary implemented. Idempotency verified by UUID and SHA-256 payload hash. Row locking (`FOR UPDATE`) on device and employee implemented in `AttendanceSyncService.php`. |
| **Offline queue/service worker** | Static shell cache and sync queue reported | **VERIFIED BY SOURCE INSPECTION** | `public/sw.js` caches static shell only (excludes admin, API, biometrics). `edifies-multidevice-kiosk.js` queues punches in IndexedDB with monotonic sequence numbers and server time anchors. |
| **Photo capture/storage** | Required workflow; previous reports discuss photo handling | **VERIFIED BY EXECUTED TEST** | Resolved in Milestone 3. Implemented live front-facing camera preview, capture, and fallback file upload on Kiosk PIN confirmation step (`/attendance`). Photo validated server-side (JPEG magic bytes `\xFF\xD8\xFF`, 200x200 to 4000x4000 dimension bounds, max 2 MB, GD raster decode). Stored privately outside webroot (`WRITEPATH . 'attendance_photos/YYYY/MM/'`) with randomized 32-hex filenames and EXIF stripping. Linked via `attendance_photos` table to `attendance_events.id` and `attendance_records.id`. Admin-only protected retrieval endpoints (`GET /admin/attendance/photos/{id}` and `/events/{id}/photo`). Manager access strictly prohibited (HTTP 403). Safe lifecycle deletes files on rejected punches (no orphaned files) and handles idempotent duplicate retries. 12/12 automated feature tests passed in `AttendancePhotoTest.php`. |
| **Reports/history** | Day-wise/month-wise and employee search required | **VERIFIED BY EXECUTED TEST** | Resolved in Milestone 3. Implemented unified Admin Attendance History & Reports (`/admin/attendance/reports`) with Daily View, Monthly Summary, and Employee Chronology tabs. Supports date picker, employee search, department and status filters, incomplete/missing exit detection for past dates, KPI summary metric cards, batch-loading photo metadata (zero N+1 queries), and photo inspection modal. Enforces server-side Admin role check (Manager access denied with 403 Forbidden). 12/12 automated feature tests passed in `AttendanceHistoryAndReportsTest.php`. |
| **Security remediation** | Credential revocation, origin controls and restore DB quarantine | **VERIFIED BY SOURCE INSPECTION** | `DeviceAuthFilter.php` enforces cookie-only auth (`edifies_kiosk_token`), SHA-256 token hashing, exact RFC 6454 origin matching, custom header checks, and fail-closed null origin handling. |
| **Responsive UI** | Mandatory owner requirement | **VERIFIED BY EXECUTED TEST** | Keypad, PIN indicators, confirmation cards, and success states styled with responsive CSS grid and flexbox, min touch targets 48px, optimized across phone (360-430px), tablet (600-900px), and desktop. Live viewport rendering verified. |
| **Production deployment** | Low-cost host not yet accepted through production test | **NOT READY** | Low-cost hosting environment not configured. Deployment and backup-restore testing remain pending. |

### 13.2 Face-recognition research

| Stage / area | Reported result | Audit Status (2026-11-05) | Audit Evidence Summary |
| :--- | :--- | :--- | :--- |
| **3.4.0 lab setup** | Separate research lab and initial inventory | **VERIFIED BY SOURCE INSPECTION** | Directory `Edifies_Face_Recognition_Lab` exists outside project root; clean isolation verified. |
| **3.4.1 model/runtime acquisition** | YuNet, SFace, ORT Web acquired; MiniFASNet blocked | **VERIFIED BY SOURCE INSPECTION** | YuNet INT8 (`100,416 B`, SHA-256 `321aa5a6...`), SFace INT8 (`9,896,933 B`, SHA-256 `2b0e941e...`), and ORT Web 1.18.0 files present on disk. MiniFASNet quarantined. |
| **3.4.1.1 verification** | Hashes and ONNX graph specs documented | **VERIFIED BY SOURCE INSPECTION** | Verification records documented in lab reports. Model files confirmed intact on filesystem. |
| **3.4.2 browser harness** | Desktop browser inference reported | **VERIFIED BY SOURCE INSPECTION** | Standalone browser prototype present in `prototype/index.html` and `prototype/app.js`. |
| **3.4.2.1 video/runtime audit** | Actual WebM ingestion, 40 passing assertions, benchmarks reported | **VERIFIED BY EXECUTED TEST** | Executed local tests: `test_core_math.js` passed **19/19** assertions; `test_video_pipeline_and_lifecycle.js` passed **21/21** assertions. Total **40/40** passed. |
| **3.4.2.2 readiness investigation** | Dataset matrix and protocol prepared; no human data collected | **VERIFIED BY SOURCE INSPECTION** | All 5 governance and protocol registers authored. Stop condition verified. |
| **Real-face detection** | No authorized human-face data in lab | **BLOCKED** | Laboratory contains zero human face imagery (`test-datasets/` only contains synthetic shapes). Blocked on Owner Decision B.1 and Legal Decision D.1. |
| **Face recognition accuracy** | No validated 1:N results | **NOT STARTED / BLOCKED** | Awaiting detection validation and 5-point affine transformation implementation. |
| **PAD/liveness** | MiniFASNet quarantined; no validated PAD | **NOT STARTED / BLOCKED** | No production-cleared, un-quarantined presentation attack detection method exists. |
| **Android performance** | No physical Android benchmark reported | **NOT STARTED** | Inference measured only on desktop AMD Ryzen 7 CPU; real-device Android latency untested. |
| **Production integration** | Not approved | **NOT STARTED** | Blocked until feasibility gates pass. |

### 13.3 Overall product state
The product is **NOT YET PRODUCTION READY**.
- **Working Core:** PHP architecture, database schema, employee directory, kiosk device pairing/revocation, append-only attendance ledger, idempotency, terminal mode enforcement, and offline Service Worker shell caching are well-engineered and sound.
- **Critical Missing Modules:**
  1. **Photo capture and storage** is completely missing in `php-attendance`.
  2. **General attendance history & reporting** (day-wise, month-wise, employee search) is completely missing in `php-attendance`.
  3. **Kiosk manual fallback** (PIN / QR / Manual Entry) is missing on the `/attendance` screen.
  4. **Manager role permissions** need tightening on `/admin/dashboard` and `/admin/face-lab`.
- **Face Recognition:** Remains strictly an isolated research initiative blocked on dataset authorization.

---

## 14. WORK PLAN — FINISH THE PRODUCT, NOT THE PAPERWORK

### Phase 0 — One-time baseline audit (COMPLETED)
Read-only cross-check of actual code, migrations, routes, tests, reports, lab artifacts and Git status. Populated the evidence ledger in this file. Application code untouched.

### Phase 1 — Finish the core attendance product (Next Priority)
Work one module at a time:
1. **Kiosk Manual Fallback & UI Completion:** Add robust, touch-friendly non-face attendance fallback (PIN keypad / Employee selection) on `/attendance` so staff can punch immediately without face recognition.
2. **Photo Capture and Private Storage:** Implement photo upload, server-side MIME/dimension validation, randomized filesystem storage outside public root, and link photo references to attendance events.
3. **Attendance History and Reports:** Port day-wise and month-wise attendance views, employee attendance history search, and irregular punch reporting to `php-attendance`.
4. **Manager Role Boundary Hardening:** Restrict Manager role strictly to Staff Directory and Kiosk views in `Routes.php` and `AdminAuthFilter.php`.
5. **Offline Queue End-to-End Validation:** Verify browser IndexedDB punch queuing and synchronization against the live API on real network disconnect/reconnect.

### Phase 2 — Real-world kiosk and reliability checks
Test on the actual low-cost Android device(s): camera behavior, screen layout, connectivity loss/recovery, repeated staff use, battery/heat, and server synchronization. Verify database backup and restore.

### Phase 3 — Optional face-recognition feasibility
Only if the owner elects to continue and the data/consent route is approved:
- Small controlled adult volunteer pilot.
- YuNet detection feasibility.
- SFace alignment/recognition feasibility only after detection is useful.
- Physical Android benchmark.
- A simple go/no-go decision against non-face alternatives.

Do not let face research hold the basic attendance application hostage.

### Phase 4 — Pilot deployment and final acceptance
Deploy to the chosen low-cost host, configure HTTPS, private photo storage, backups and recovery. Run a limited operational pilot, resolve defects and obtain owner acceptance before normal use.

---

## 15. ANTIGRAVITY OPERATING RULES — READ THIS BEFORE EVERY TASK

Antigravity must follow these rules for every owner request.

### 15.1 Read this master file first
Before inspecting or editing anything, read this complete file. Treat it as the project source of truth. If a new owner instruction conflicts with this document, the latest explicit owner instruction wins; update the relevant decision/status section after approval.

### 15.2 Understand the request before acting
- Restate the requested outcome in one or two sentences.
- Identify the exact module/files likely involved.
- Inspect existing structure and implementation first.
- Reuse existing patterns where they are sound.
- Do not ask the owner to repeat information already in this file or earlier project context.
- Ask one concise question only when an essential decision cannot safely be inferred. Otherwise make a conservative, reversible assumption and state it.

### 15.3 One task at a time
- Do only the task the owner requested.
- Do not start the next stage automatically.
- Do not add optional features, frameworks or unrelated refactors.
- Stop after implementation, tests and the requested summary.
- Wait for the owner's review and explicit authorization before another major task.

### 15.4 Inspect before modifying
Before editing:
- Inspect the current file tree and relevant source files.
- Inspect routes, controllers, models, migrations, views, JavaScript and tests relevant to the task.
- Check current Git status and preserve pre-existing changes.
- Identify external dependencies and affected modules.
- Make a concise change plan.
Do not replace files wholesale when a small, safe edit is enough.

### 15.5 Protected boundaries
- PHP application changes must stay inside `php-attendance`, unless the owner explicitly authorizes a specific root-level change.
- Face research changes must stay inside `Edifies_Face_Recognition_Lab`.
- The legacy Java/Spring Boot application and PostgreSQL database are preserved; do not modify them without explicit authorization.
- Never expose credentials, tokens, PINs, private keys, personal photos or biometric data in reports or chat.
- Never run destructive Git commands (reset, clean, forced checkout, history rewrite) or commit/stage/push without explicit approval.
- Never alter or delete databases, backups, photos or employee records without explicit approval.
- Never buy services, create paid resources or incur charges without explicit approval.

### 15.6 Keep implementation simple
- Prefer existing CodeIgniter features and a small number of focused PHP services.
- Do not introduce new dependencies unless necessary; explain installation, hosting and maintenance cost before asking approval.
- No microservices or infrastructure expansion.
- No unrelated redesign.
- No unnecessary documentation files.
- No speculative production configuration.

### 15.7 Definition of done for each requested module
Every completed module must pass all three owner requirements:
- **A. Functionality** — required features, normal flow, error flow, validation, permissions and data correctness.
- **B. UI** — correct alignment, spacing, fonts, styles, clear messages and consistent design.
- **C. Responsiveness** — phone, tablet/kiosk, small/medium laptop and large laptop/desktop.

Also run relevant regression tests and state clearly what was not tested.

### 15.8 Evidence-based reporting
Use only these labels:
- `VERIFIED BY EXECUTED TEST`
- `VERIFIED BY SOURCE INSPECTION`
- `REPORTED BY PRIOR STAGE — NOT REPRODUCED`
- `PARTIALLY VERIFIED`
- `NOT VERIFIED`
- `BLOCKED`

For test results include command, environment, actual counts and failures. For manual testing state the exact browser/device/viewport. Never claim physical Android testing from desktop emulation.

### 15.9 Update this file — not a new report collection
At the end of each owner-approved task:
- Update the relevant module/status rows in Section 13.
- Add a concise dated entry to the work log below.
- Update any architecture/decision item that genuinely changed.
- Record files changed and tests executed.
- Do not create separate Markdown reports unless the owner requests them or a specific deliverable genuinely requires one.
Do not mark an item VERIFIED just because a prior AI report says so.

---

## 16. FIRST ANTIGRAVITY TASK — COMPLETE CROSS-PROJECT BASELINE AUDIT

**Audit Date:** 2026-11-05  
**Audit Execution Mode:** Strictly Read-Only  
**Audit Scope:** `php-attendance`, root legacy Java workspace, `Edifies_Face_Recognition_Lab`, and historical reports.

### 16.1 Actual Environment and Technology Versions
- **Audit Timestamp:** 2026-11-05 21:45:00 IST (+05:30)
- **Host OS:** Microsoft Windows 11 Home Single Language (Version 10.0.26300, Build 26300 x64)
- **PHP CLI Runtime:** PHP 8.5.12 (cli) (ZTS Visual C++ 2022 x64, Zend OPcache v8.5.12) at `C:\tools\php85\php.exe`
  - **Required Extensions:** `extension=gd` enabled in `C:\tools\php85\php.ini` (mandatory deployment prerequisite for raster verification, EXIF stripping, and JPEG normalization). Supported: JPEG, PNG, WebP.
- **Composer Runtime:** Version 2.11.1 (2026-10-30) at `C:\tools\php85\composer.phar`
- **Framework:** CodeIgniter 4 (Locked version: `v4.7.6`, verified in `php-attendance/composer.lock`)
- **PHPUnit Engine:** Version 10.5.66 by Sebastian Bergmann at `php-attendance/vendor/bin/phpunit`
- **JavaScript & Node Engine:** Node.js v24.19.0, npm 11.17.0
- **Database Connection Target:** MariaDB 11.4 LTS on port 3307 (`attendance_dev`, `attendance_test` declared in `php-attendance/.env`)
- **Legacy Application Stack:** Java 21 / Spring Boot 3.3.4 / PostgreSQL port 5435
- **Redaction Certification:** Zero passwords, database credentials, device tokens, or private hashes were printed or exposed during inspection.

### 16.2 Actual Project Structure
```
C:\Users\sriva\OneDrive\Desktop\Edifies_School\ (Git Root, Legacy Java Application)
├── pom.xml                                      # Spring Boot 3.3.4 / Java 21 build descriptor
├── src/main/java/com/edifies/attendance/        # Complete legacy Java Spring Boot backend
├── src/main/resources/                          # Application properties, Flyway migrations, static assets
├── photos/                                      # Legacy private photo storage directory
├── backups/                                     # Disaster recovery SQL/ZIP archives
│
├── php-attendance/                              # Active CodeIgniter 4 PHP Production Target
│   ├── app/
│   │   ├── Config/ (Routes.php, Filters.php, Database.php, App.php, etc.)
│   │   ├── Controllers/
│   │   │   ├── Admin/ (AuthController, DashboardController, EmployeeController, KioskDeviceController, AttendanceReviewController, FaceLabController)
│   │   │   ├── Api/ (KioskApiController, AdminApiController)
│   │   │   └── AttendanceController.php, BaseController.php, Home.php
│   │   ├── Database/
│   │   │   ├── Migrations/ (8 versioned migrations: 2026-11-03-000001 to 000008)
│   │   │   └── Seeds/ (InitialDevSeeder.php)
│   │   ├── Filters/ (AdminAuthFilter, DeviceAuthFilter, HttpsEnforcementFilter, RateLimitFilter)
│   │   ├── Models/ (EmployeeModel, KioskDeviceModel, AttendanceRecordModel, AttendanceEventModel, AdminUserModel, AdminAuditLogModel, AttendanceReviewDecisionModel, IdempotencyKeyModel)
│   │   ├── Services/ (AttendanceSyncService.php)
│   │   └── Views/ (admin/..., attendance/kiosk.php, home.php)
│   ├── public/ (index.php, sw.js, manifest.json, css/style.css, js/edifies-multidevice-kiosk.js, js/kiosk-clock.js)
│   ├── tests/ (unit/..., feature/..., database/..., concurrency/...)
│   ├── composer.json, composer.lock, phpunit.dist.xml, spark, .env
│
└── C:\Users\sriva\OneDrive\Desktop\Edifies_Face_Recognition_Lab\ (Isolated Research Lab)
    ├── models/
    │   ├── yunet/ (face_detection_yunet_2023mar_int8.onnx - 100,416 B)
    │   ├── sface/ (face_recognition_sface_2021dec_int8.onnx - 9,896,933 B)
    │   ├── minifasnet/ (QUARANTINED - README.md only, zero binaries)
    │   └── onnxruntime-web/ (ort.min.js, ort-wasm.wasm, ort-wasm-simd.wasm)
    ├── prototype/ (index.html, app.js, styles.css, serve.py, tests/...)
    ├── test-datasets/ (non_biometric_pipeline_test.webm - 313,056 B synthetic fixture)
    ├── benchmarks/ (STAGE_3_4_2_1_VIDEO_AND_INFERENCE_BENCHMARKS.json)
    └── reports/ (19 audit and governance registers)
```

### 16.3 Application Feature Status Matrix

| Module / Requirement | Code Location | Observed Implementation State | Status | Missing Work / Gaps |
| :--- | :--- | :--- | :--- | :--- |
| **Admin Login & Throttling** | `Admin\AuthController.php`, `AdminUserModel.php`, `RateLimitFilter.php` | Bcrypt hashing, session generation, IP rate limiting, CSRF form protection. | **VERIFIED BY SOURCE INSPECTION** | None for core login. |
| **Manager Role Restrictions** | `AdminAuthFilter.php`, `Routes.php`, `AuthController.php`, `DashboardController.php`, `FaceLabController.php` | `ROLE_MANAGER` defined and strictly enforced. Restricted to Staff Directory (read-only) and Kiosk. Direct URL access to `/admin`, `/admin/dashboard`, `/admin/face-lab`, `/face-lab`, `/admin/kiosks`, `/admin/attendance/review`, `/api/admin/stats` returns HTTP 403 Forbidden. Navigation Dashboard button hidden. Login redirects to `/admin/employees`. | **VERIFIED BY EXECUTED TEST** | None. Resolved in Task 1. |
| **Staff Directory (CRUD)** | `Admin\EmployeeController.php`, `EmployeeModel.php`, `views/admin/employees/` | Full listing, search, department/status filter, pagination, toggle active/inactive, unique validation. | **VERIFIED BY SOURCE INSPECTION** | Complete. Manager role has read-only access; Admin has full mutation rights. |
| **Kiosk Device Management** | `Admin\KioskDeviceController.php`, `Api\KioskApiController.php` | 128-bit pairing codes, 30-day token rotation, status revocation, audit logging. | **VERIFIED BY SOURCE INSPECTION** | Fully working server-side pairing and revocation. |
| **Device Authentication** | `DeviceAuthFilter.php` | `edifies_kiosk_token` cookie only, SHA-256 token hashing, exact origin match, CSRF custom header check. | **VERIFIED BY SOURCE INSPECTION** | Verified Stage 3.3.2.2 security remediation. |
| **Attendance Ledger & Rules** | `AttendanceSyncService.php`, `attendance_events`, `attendance_records` | Append-only ledger, daily summary aggregate, `ENTRY_ONLY`/`EXIT_ONLY` modes, `SELECT FOR UPDATE` locks. | **VERIFIED BY SOURCE INSPECTION** | Robust idempotency and concurrency locking verified. No auto-checkout heuristics. |
| **Manual Attendance Fallback** | `app/Views/attendance/kiosk.php` | Screen shows "Zero-PIN" message and camera viewport placeholder only. | **NOT STARTED (MISSING)** | **CRITICAL GAP:** No PIN keypad, QR scanner, or employee punch fallback exists on the kiosk view. |
| **Attendance History & Reports** | N/A in `php-attendance` | Only `AttendanceReviewController.php` exists (for held/quarantined punches). | **NOT STARTED (MISSING)** | **CRITICAL GAP:** General day-wise and month-wise attendance reports and employee attendance history are absent. |
| **Photo Capture & Storage** | N/A in `php-attendance` | Zero photo controllers, zero photo upload endpoints, zero photo database columns. | **NOT STARTED (MISSING)** | **CRITICAL GAP:** Photo capture, validation, private storage, and linkage to attendance records are absent in PHP. |
| **Offline Queue & PWA Shell** | `public/sw.js`, `public/js/edifies-multidevice-kiosk.js` | Service Worker caches shell only. IndexedDB stores offline punches with sequence counters and time anchors. | **VERIFIED BY SOURCE INSPECTION** | Shell caching and queue architecture verified. End-to-end sync needs physical device test. |
| **Audit Logging** | `AdminAuditLogModel.php` | Captures login failures, pairing, token rotations, out-of-order sequences, reviews. Redacts secrets. | **VERIFIED BY SOURCE INSPECTION** | Verified. |

### 16.4 Role and Permission Audit
- **Admin Role (`ROLE_ADMIN`):** Full authorized administrative control across Dashboard, Employee Directory (Add, Edit, Soft-Delete, Status Toggle), Kiosk Devices (Create, Generate Pairing Codes, Revoke), Attendance Review (Approve/Reject held punches), and Security Audits.
- **Manager Role (`ROLE_MANAGER`):**
  - *Intended Scope (Section 6.1):* Access strictly limited to Staff Directory and Attendance Kiosk.
  - *Actual Implementation:*
    - `Admin\EmployeeController.php`: Properly enforces `requireAdminRole()`; Managers can view the directory but cannot add, edit, or delete staff.
    - `Routes.php`: Groups `kiosks` and `attendance/review` are properly protected by `filter => 'adminAuth:ADMIN,ROLE_ADMIN'`, forbidding Manager access.
    - *Boundary Hardened (Task 1):* `/admin`, `/admin/dashboard`, `/admin/face-lab`, `/face-lab`, `/admin/kiosks`, `/admin/attendance/review`, and `/api/admin/stats` explicitly require `ADMIN,ROLE_ADMIN`. Direct manager access produces HTTP 403 Forbidden. Navigation items hidden. Login redirects to `/admin/employees`. Fully verified by automated test suite.

### 16.5 Attendance Transaction & Idempotency Audit
- **Ledger Invariant:** Dual-table design: append-only ledger `attendance_events` (storing every raw punch attempt with telemetry) and aggregate summary table `attendance_records` (storing official daily shifts).
- **Idempotency Strategy:**
  1. Client generates UUIDv4 (`client_uuid`) upon punch creation.
  2. Server calculates canonical SHA-256 payload hash (`client_uuid`, `employee_id`, `device_id`, normalized timestamp).
  3. Pre-transaction and in-transaction lookups detect identical client UUIDs and confirm idempotency without duplicating records.
  4. Payload mismatches for the same UUID are quarantined and logged as security events.
- **Concurrency & Locking:**
  - Implements consistent row lock ordering in `AttendanceSyncService.php`:
    ```sql
    SELECT id FROM kiosk_devices WHERE id = ? FOR UPDATE;
    SELECT id FROM employees WHERE id = ? FOR UPDATE;
    ```
  - Eliminates deadlocks between concurrent kiosk devices and prevents double check-ins.
- **Strict Terminal Modes:** Enforces server-authoritative `ENTRY_ONLY` and `EXIT_ONLY` state transitions. Arbitrary 5-minute automatic checkout heuristics are completely eliminated.

### 16.6 Kiosk and Device Audit
- **Device Pairing:**
  - Admin generates a 128-bit high-entropy pairing code (32 hex characters) valid for 10 minutes in `Admin\KioskDeviceController.php`.
  - Kiosk sends code to `/api/kiosk/pair`.
  - Rate limiting protects pairing (max 5 attempts/min per IP; max 30 global).
  - Pairing exchanges code for a 256-bit device token and issues an initial device epoch ID.
- **Credential Storage & Transport:**
  - Raw tokens are **never** returned in JSON response bodies and **never** stored in browser `localStorage`.
  - Transport is strictly via `edifies_kiosk_token` cookie: `HttpOnly; SameSite=Strict; Path=/api/kiosk; Secure` (in production).
  - Server database stores only SHA-256 token hashes (`kiosk_devices.device_token_hash`).
- **Origin & CSRF Protections:**
  - `DeviceAuthFilter.php` validates exact canonical RFC 6454 origin matching (scheme, host, port).
  - Rejects `null` or opaque origins.
  - Requires custom headers (`X-Requested-With` or `X-CSRF-TOKEN`/`X-Kiosk-CSRF`) on all state-changing endpoints.

### 16.7 Photo Capture and Storage Audit
- **Legacy Java Application:** Implemented in `AttendancePhotoService.java` and `LocalFilePhotoStorageService.java`, storing files in `photos/` and metadata in PostgreSQL.
- **PHP Attendance Application (`php-attendance`):**
  - **Status: NOT STARTED / COMPLETELY MISSING.**
  - There is no photo upload controller.
  - There is no file storage service in `app/Services/`.
  - `attendance_events` and `attendance_records` contain no `photo_path` or `photo_id` columns.
  - The kiosk view has only an empty SVG camera box placeholder.
  - **Verdict:** Photo capture and private storage must be ported from the legacy Java architecture to the PHP stack as a top Phase 1 priority.

### 16.8 Offline Synchronization Audit
- **Static Shell Caching:** Handled by `public/sw.js` (cache `edifies-kiosk-shell-v1.3.0`). Pre-caches `/attendance`, CSS, and JavaScript. Explicitly bypasses `/admin`, `/api`, and biometrics.
- **Offline Attendance Queue:** Handled by `public/js/edifies-multidevice-kiosk.js` using browser IndexedDB (`EdifiesAttendanceDB`).
  - Punches are queued locally when offline with monotonic sequence counters.
  - Synchronizes to `/api/kiosk/attendance/sync` when connectivity resumes.
- **Server-Time Anchoring:**
  - To prevent device clock manipulation, `/api/kiosk/time-anchor` issues cryptographically bound time anchors (`kiosk_time_anchors`) containing server UTC time and browser session hashes.
  - When offline punches sync, the server computes elapsed time from the anchor. Punches with broken anchors or excessive drift are automatically held for administrative review (`REVIEW_REQUIRED`) without corrupting daily records.

### 16.9 Face-Research Evidence Audit (`Edifies_Face_Recognition_Lab`)
- **Acquired Model Artifacts (Disk Inspection):**
  1. `face_detection_yunet_2023mar_int8.onnx`: 100,416 bytes, SHA-256 `321aa5a6afabf7ecc46a3d06bfab2b579dc96eb5c3be7edd365fa04502ad9294`.
  2. `face_recognition_sface_2021dec_int8.onnx`: 9,896,933 bytes, SHA-256 `2b0e941e6f16cc048c20aee0c8e31f569118f65d702914540f7bfdc14048d78a`.
  3. `ort.min.js`: 540,716 bytes, SHA-256 `74945866728300a05746a37387ac48863a8be5cdc3760304b47d0136d7d0dc5b`.
  4. `ort-wasm-simd.wasm`: 10,595,041 bytes, SHA-256 `f533b5f21790563d7556c611c3835ac65848ee326da2689aac54ed7d9cc8c6a4`.
  5. `ort-wasm.wasm`: 9,758,086 bytes, SHA-256 `ebe7ec327f83aabc07e3b58487522481ccff10941f24182ab0428d0123652ff2`.
  6. `minifasnet`: QUARANTINED. Zero binaries acquired.
- **Demonstrated Research Results:**
  - Offline browser inference execution verified using WebAssembly.
  - Frame sampling cadence, frame locking, and deterministic tensor disposal verified.
  - Desktop AMD Ryzen 7 benchmark: ~165 ms inference latency reported.
- **Untested & Unverified Areas:**
  - Real-face detection accuracy: 0% tested (zero human face imagery in lab).
  - Physical Android hardware performance: 0% tested (tested only on desktop PC).
  - Biometric 1:N recognition accuracy: 0% tested.
  - Presentation attack detection (PAD): 0% tested (no approved model).

### 16.10 Previous Stage Claim Reconciliation

| Prior Report Claim | Source File / Test Inspected | Finding & Evidence | Reconciled Status |
| :--- | :--- | :--- | :--- |
| **Compromised credential DEV-TABLET-001 contained (Stage 3.3.2.2)** | `DeviceAuthFilter.php`, `InitialDevSeeder.php` | Constant hash removed from seeder; device revocation verified; cookie-only auth enforced. | **VERIFIED BY SOURCE INSPECTION** |
| **Strict Origin matching prevents localhost/127.0.0.1 confusion (Stage 3.3.2.2)** | `DeviceAuthFilter.php` lines 105-173 | Canonical RFC 6454 scheme, host, and port matching implemented. Null/opaque origin rejected. | **VERIFIED BY SOURCE INSPECTION** |
| **Service Worker never caches admin or API responses (Stage 3.2.4)** | `public/sw.js` lines 63-76 | Explicit `if (url.pathname.startsWith('/admin') \|\| url.pathname.startsWith('/api')) return;` present. | **VERIFIED BY SOURCE INSPECTION** |
| **Append-only attendance ledger with server time anchors (Stage 3.2.4)** | `AttendanceSyncService.php`, Migrations 000004 & 000007 | Ledger table `attendance_events` and anchor table `kiosk_time_anchors` fully verified in source. | **VERIFIED BY SOURCE INSPECTION** |
| **40 deterministic video and math assertions pass in lab (Stage 3.4.2.1)** | `test_core_math.js`, `test_video_pipeline_and_lifecycle.js` | Executed safe local Node commands: 19/19 math tests passed, 21/21 pipeline tests passed. | **VERIFIED BY EXECUTED TEST** |
| **Real-face detection validated in lab** | `test-datasets/` folder | Lab contains only `non_biometric_pipeline_test.webm` (synthetic shapes). Zero human faces. | **REFUTED / BLOCKED (ACCURATELY REPORTED AS BLOCKED IN 3.4.2.2)** |
| **Attendance system has working photo capture in PHP** | `php-attendance/app/` tree | No photo code exists in PHP. Existed only in legacy Java (`AttendancePhotoService.java`). | **REFUTED — NOT IMPLEMENTED IN PHP** |
| **Attendance reports (day/month) available in PHP** | `app/Controllers/Admin/` | Only `AttendanceReviewController.php` exists. Legacy Java had `AdminReportController.java`. | **REFUTED — NOT IMPLEMENTED IN PHP** |

### 16.11 Safe Test Commands and Actual Execution Results
1. **PHP Framework Health Test:**
   - **Command:** `& "C:\tools\php85\php.exe" "vendor/bin/phpunit" tests/unit/HealthTest.php` (Working directory: `php-attendance`)
   - **Output:** `OK (2 tests, 3 assertions)`, Memory: 12.00 MB, Time: 00:02.193.
   - **Evidence Label:** `VERIFIED BY EXECUTED TEST`.
2. **Face Recognition Lab Core Math Tests:**
   - **Command:** `node "C:\Users\sriva\OneDrive\Desktop\Edifies_Face_Recognition_Lab\prototype\tests\test_core_math.js"`
   - **Output:** `19/19 TESTS PASSED` (Letterboxing scale/pad, landmark inverse mapping, L2 normalization, NMS, synthetic YuNet decoding).
   - **Evidence Label:** `VERIFIED BY EXECUTED TEST`.
3. **Face Recognition Lab Video Pipeline & Lifecycle Tests:**
   - **Command:** `node "C:\Users\sriva\OneDrive\Desktop\Edifies_Face_Recognition_Lab\prototype\tests\test_video_pipeline_and_lifecycle.js"`
   - **Output:** `21/21 TESTS PASSED` (MIME/extension validation, MediaError mapping, sampling cadence, frame locking, tensor disposal).
   - **Evidence Label:** `VERIFIED BY EXECUTED TEST`.
4. **Database-Backed Test Suites (Unrun):**
   - In accordance with Section 6 of the prompt ("Do not connect to MariaDB or PostgreSQL during this baseline audit. Do not run migrations, seeders, write tests or tests that mutate data"), tests in `tests/database/`, `tests/feature/`, and `tests/concurrency/` were **NOT RUN** to guarantee zero data mutation.

### 16.12 UI and Responsive-Design Findings
- **Admin Views:** Styled with modern CSS in `public/css/style.css`.
  - Desktop & Laptop ($1024\text{ px} - 1440\text{ px}+$): Clean responsive grid, intuitive tables, sidebars, and stat cards.
  - Tablet ($768\text{ px} - 1024\text{ px}$): Functional table scrolling and stacked metrics.
  - Mobile ($360\text{ px} - 430\text{ px}$): Usable hamburger layouts, but complex tables require horizontal scroll.
- **Kiosk View (`/attendance`):**
  - Displays live IST clock, full-screen card, and status badges.
  - **Defect:** Contains no interactive controls (no keypad, buttons, or inputs). Designed exclusively around a theoretical touchless camera loop that does not yet have real face detection enabled.

### 16.13 Known Defects and Risks Ranked by Practical Severity

| # | Severity | Affected Module | Evidence / Description | Practical Remediation Plan |
| :- | :--- | :--- | :--- | :--- |
| **D.1** | ~~HIGH~~ **RESOLVED** | Kiosk Presentation (`app/Views/attendance/kiosk.php`, CSS, JS) | Kiosk UI displayed "Zero-PIN" and an empty camera box without manual fallback. | **RESOLVED in Task 2.** Implemented on-screen numeric keypad, visual progress dots, two-step confirmation, single-use verification token, rate-limiting, and canonical `AttendanceSyncService` integration. Verified by 15 automated feature tests. |
| **D.2** | ~~HIGH~~ **RESOLVED** | Photo Module (`php-attendance`) | No photo capture, upload, validation, or private storage exists in PHP. Breaks core school audit requirement. | **RESOLVED in Milestone 3.** Implemented `AttendancePhotoService`, `AttendancePhotoModel`, migration `2026-11-06-000001`, private disk storage outside webroot (`writable/attendance_photos/YYYY/MM/`), randomized 32-hex filenames, EXIF stripping, kiosk live camera capture & fallback file upload, and protected admin retrieval endpoints. Verified by 12 automated feature tests in `AttendancePhotoTest.php`. |
| **D.3** | ~~HIGH~~ **RESOLVED** | Reports Module (`php-attendance`) | Administrators and managers cannot view day-wise/month-wise attendance summaries or search employee history. | **RESOLVED in Milestone 3.** Implemented `Admin\AttendanceReportController`, unified responsive view `app/Views/admin/attendance_reports/index.php` (Day-wise, Monthly summary, and Employee chronology), missing exit / incomplete shift detection, summary KPI cards, photo modal viewer, and batch photo loading (zero N+1 queries). Manager access strictly blocked (HTTP 403). Verified by 12 automated feature tests in `AttendanceHistoryAndReportsTest.php`. |
| **D.4** | ~~MEDIUM~~ **RESOLVED** | Role Enforcement (`app/Config/Routes.php`, Filters, Controllers) | `/admin/dashboard` and `/admin/face-lab` were accessible to `ROLE_MANAGER`. | **RESOLVED in Task 1.** Restricted to `ADMIN,ROLE_ADMIN`, 403 Forbidden enforced, UI navigation hidden, login redirection corrected, verified by 14 automated tests. |
| **D.5** | **LOW** | Hardware Validation (Kiosk PWA) | All benchmarks and tests have been performed on desktop CPU. Actual Android WebView performance is unknown. | Benchmark on physical target Android tablet/phone before enabling camera loops. |

### 16.14 Architecture Deviations
1. ~~Missing Photo & Reporting Modules~~: **Resolved in Milestone 3.** Photo capture, validation, private storage, and unified reporting fully operational.
2. ~~Missing Kiosk Manual Fallback~~: **Resolved in Task 2.** Non-face PIN attendance fallback fully operational.
3. **Advanced Event Ledger & Monotonic Sequencing (Positive Deviation):** The implementation of `attendance_events`, `kiosk_time_anchors`, monotonic sequence validation, and `SELECT FOR UPDATE` locking exceeds standard basic attendance architectures and provides high defense against time spoofing and network race conditions.

### 16.15 Remaining Implementation Work
To achieve a dependable, usable pilot deployment for Edifies School:
1. ~~**Phase 1, Task 1.1:** Add Kiosk Non-Face Fallback (PIN keypad / Employee punch UI on `/attendance`).~~ **[COMPLETED — 2026-11-05]**
2. ~~**Phase 1, Task 1.2:** Implement Photo Capture & Private Storage in PHP (Upload, validation, private storage, event linking).~~ **[COMPLETED — 2026-11-06]**
3. ~~**Phase 1, Task 1.3:** Implement Attendance History & Reporting in PHP (Day-wise, month-wise, employee search).~~ **[COMPLETED — 2026-11-06]**
4. ~~**Phase 1, Task 1.4:** Tighten Manager Role Boundaries in `Routes.php` & Filters.~~ **[COMPLETED — 2026-11-05]**
5. ~~**Phase 1, Task 1.5:** Milestone 3.1 Attendance System Stabilization & Operational Readiness (Reverted testing bypass, enforced strict server time checks, PNG/JPEG normalization, backup restore test, GD audit, security response verification; 230 passing tests).~~ **[COMPLETED — 2026-11-06]**
6. ~~**Phase 2, Task 2.1:** Validate Kiosk PWA on Physical Android Phone/Tablet & Offline IndexedDB Sync Queue End-to-End.~~ **[PARTIALLY VALIDATED — 2026-11-06]** (Headless Chrome Mobile Emulation (Pixel 7 412x915) passed end-to-end; physical Android hardware not connected; status: NOT TESTED on physical device).
7. ~~**Phase 2, Task 2.2:** Execute Full Database & Photo Backup and Restore Verification.~~ **[COMPLETED — 2026-11-06]** (Full MariaDB dump and photo archive created; 100% restored and validated on disposable database and temp directory; relational integrity verified; 247 tests passing).
8. **Phase 2, Task 2.3:** Physical Android Tablet/Phone Pilot Sign-Off (Requires Owner to attach USB device or access via LAN).

### 16.16 Recommended Next Development Task
> [!TIP]
> **Recommended Next Task: Phase 2, Task 2.3 — Physical Android Device Attachment & On-Device Sign-Off**  
> Connect physical Android device using `adb reverse tcp:8088 tcp:8088` to test real touchscreen, camera autofocus, and PWA installation; complete final operational handover for affordable hosting deployment.

---

## 17. LIVING WORK LOG

| Date | Task | Result | Evidence / Status | Owner approval for next task |
| :--- | :--- | :--- | :--- | :--- |
| **2026-11-05** | Master Architecture & AI Working Guide consolidated from project records. | Baseline guide placed in project root. | Awaiting read-only baseline audit. | Not yet given. |
| **2026-11-05** | Complete Cross-Project Read-Only Baseline Audit (Section 16). | Baseline established. PHP 8.5 / CI 4.7.6 verified. 40/40 lab tests passed. Core attendance ledger sound. Identified 3 critical missing modules: Photo storage, Attendance reports, Kiosk manual fallback. Zero application code modified. | **BASELINE ESTABLISHED** (Verified via code inspection, file hashes, and safe test execution). | Owner authorized Task 1: Fix Manager Access Permissions. |
| **2026-11-05** | **Task 1 — Fix Manager Access Permissions** | Corrected and verified Manager permissions. Admin retains full access; Manager strictly restricted to Staff Directory (read-only) and Attendance Kiosk. Direct URL access to `/admin`, `/admin/dashboard`, `/admin/face-lab`, `/face-lab`, `/admin/kiosks`, `/admin/attendance/review`, `/api/admin/stats` returns HTTP 403 Forbidden. Navigation Dashboard button hidden. Login redirects to `/admin/employees`. | **VERIFIED BY EXECUTED TEST** (14/14 automated assertions passed in `ManagerAccessPermissionsTest.php`; 103/103 total PHPUnit tests passing). | Owner authorized Task 2: Simple Employee PIN Attendance Fallback. |
| **2026-11-05** | **Task 2 — Simple Employee PIN Attendance Fallback** | Implemented touch-friendly 6-digit employee PIN fallback on `/attendance`. Admin can assign/reset PIN (bcrypt hashed); Managers forbidden (403); raw PIN never logged or exposed. Kiosk features on-screen keypad, 6 visual dots, physical keyboard support, and a two-step flow (Verification -> Confirmation displaying full name and terminal action). Rate-limited (5 attempts/60s). Single-use token prevents unverified attendance. Integrates with canonical `AttendanceSyncService` respecting server-authoritative terminal mode (`ENTRY_ONLY` / `EXIT_ONLY`). Zero schema changes. | **VERIFIED BY EXECUTED TEST** (15/15 automated feature tests passed in `EmployeePinAttendanceTest.php`; 200/200 total PHPUnit tests passing with 752 assertions). | Owner authorized Milestone 3: Complete Attendance Photos, History & Reporting. |
| **2026-11-06** | **Milestone 3 — Complete Attendance Photos, History & Reporting** | Completed the two major missing components of the attendance application. **Part A:** Kiosk live front-facing camera preview, capture, and upload fallback; server-side JPEG magic bytes verification, bounds checking (200x200 to 4000x4000), GD raster decoding, EXIF metadata stripping; private storage in `WRITEPATH . 'attendance_photos/YYYY/MM/'` with random 32-hex filenames; database linking via `attendance_photos` table; transaction cleanup on rejected punches; idempotent duplicate retry handling; Admin-only retrieval endpoints (`GET /admin/attendance/photos/{id}` and `/events/{id}/photo`) with security headers; Manager access strictly blocked (HTTP 403). **Part B:** Unified Admin Attendance History & Reports (`/admin/attendance/reports`) with Day-wise, Monthly summary, and Employee chronology tabs; date picker, staff search, department/status filtering, missing exit detection for past dates, KPI summary cards, photo modal inspector, zero N+1 queries via batch-loading photo metadata. | **VERIFIED BY EXECUTED TEST** (24 new automated feature tests: 12 in `AttendancePhotoTest.php` and 12 in `AttendanceHistoryAndReportsTest.php`; full regression suite passed: **224 tests, 850 assertions, 0 errors, 0 failures, ALL GREEN**). | Owner authorized Milestone 3.1: Attendance System Stabilization & Operational Readiness. |
| **2026-11-06** | **Milestone 3.1 — Attendance System Stabilization & Operational Readiness** | Audited and stabilized newly implemented PIN attendance, photo capture, private storage, and reporting modules for real Android kiosk testing. **1. Engine Time Integrity:** Reverted temporary `ENVIRONMENT !== 'testing'` bypass in `AttendanceSyncService.php` (lines 286, 341); restored strict production time rules in all environments: online future timestamps (> 60s) rejected with `INVALID_FUTURE_TIMESTAMP`; offline future monotonic drift (> 5s) held for review with `SUSPICIOUS_DRIFT`. Updated test fixtures across database, feature, and multi-process concurrency suites to time-independent relative offsets (eliminating early-morning false future rejections). **2. Photo Workflow & Format Normalization:** Expanded `AttendancePhotoService` to detect both JPEG (`\xFF\xD8\xFF`) and PNG (`\x89PNG\r\n\x1a\n`) signatures, alpha-blend transparent PNGs onto solid white canvas, strip EXIF, and normalize to standard JPEG in private partitioned storage (`attendance_photos/YYYY/MM/`); wrapped DB insert in try/catch to unlink orphan files on failure; confirmed duplicate retry unlinking without overwriting. **3. Database & Backup Verification:** Confirmed schema parity between `attendance_dev` and `attendance_test` (migration `2026-11-06-000001` in batch 10); verified pre-milestone 3 backup at `C:\tools\mariadb_backups\attendance_dev_pre_milestone3.sql` (89,038 bytes) via disposable restore test on `attendance_restore_test_disposable` (cleanly dropped after verification). **4. Runtime Environment Audit:** Documented `extension=gd` in `C:\tools\php85\php.ini` as a mandatory production hosting requirement. **5. Security Headers:** Verified protected photo endpoints enforce Admin role (403 on Manager, 302/401 unauthenticated), anti-traversal paths, `image/jpeg` content type, `nosniff`, and private no-cache headers. | **VERIFIED BY EXECUTED TEST** (Full regression suite passed: **230 tests, 885 assertions, 0 errors, 0 failures, ALL GREEN**; all 9 multi-process concurrency scenarios passed 100%). | Owner authorized Task 3.2: Final Photo/Attendance Consistency Check. |
| **2026-11-06** | **Task 3.2 — Final Photo/Attendance Consistency Check** | Verified and hardened transactional consistency and lifecycle recoverability between attendance events and required photographs across all 7 critical scenarios. **1. Atomic DB Ingestion:** Migrated photo metadata insertion directly inside the single atomic transaction (`transBegin` ... `transCommit`) under row locks in `AttendanceSyncService::processPunch()`, ensuring `attendance_events`, `attendance_records`, and `attendance_photos` are committed atomically. **2. Rollback & Orphan Cleanup (Scenarios 1, 2, 3, 4):** If punch is rejected (`REJECTED_NO_OPEN_RECORD`, `QUARANTINED`, validation errors) or if photo metadata insertion/database connection fails inside the transaction, the transaction rolls back completely (zero attendance events or daily summaries saved) and the temporary physical photo is immediately deleted from disk (`deletePhysicalFile()`). Never silently accepts attendance without its required photo. **3. Retry & Concurrency Idempotency (Scenarios 5 & 6):** On network timeout retries (`IDEMPOTENT_CONFIRMED`) or concurrent submissions with identical `client_uuid`, existing photo ID is returned, original photo file is preserved, and newly uploaded redundant photo file is safely unlinked without overwriting. **4. Conflicting Replay Protection (Scenario 7):** Submitting identical `client_uuid` with conflicting timestamp/payload is rejected with HTTP 409 `CONFLICT_PAYLOAD_MISMATCH`; uploaded photo is unlinked; original photo remains unaltered. **5. Enum Boundary Protection:** Fixed enum mismatch on repeated entry terminal scans (`DUPLICATE_IGNORED` / `PRESENCE_CONFIRMED`): redundant photo is discarded and initial `CHECK_IN` photo ID is returned, preventing MySQL data truncation error on `photo_type` ENUM('CHECK_IN', 'CHECK_OUT'). **6. Kiosk UI Safeguard:** Updated `kiosk-pin-attendance.js` to block punch confirmation if no photo has been captured or uploaded, warning the user. | **VERIFIED BY EXECUTED TEST** (6 new automated feature tests in `PhotoAttendanceConsistencyTest.php`; full regression suite passed: **236 tests, 940 assertions, 0 errors, 0 failures, ALL GREEN**). | Owner authorized Phase 2: Android Kiosk Pilot, Camera Testing & End-to-End Attendance Validation. |
| **2026-11-06** | **Phase 2 — Android Kiosk Pilot, Camera Testing & End-to-End Attendance Validation** | Validated kiosk workflow, camera/photo capture, responsive layout, PWA assets, and admin reporting in headless Chrome mobile emulation (Pixel 7 at 412x915). **1. Hardware Discovery:** Verified no physical Android hardware or emulator is currently connected via USB/PnP (`adb` not installed on PATH; no Android devices found). Marked physical device testing as **NOT TESTED**; overall verdict **B. PARTIALLY VALIDATED — PHYSICAL TESTS INCOMPLETE**. Documented complete owner instructions for ADB reverse. **2. Strict Database Isolation:** Operated strictly against `attendance_test` on MariaDB port 3307 using synthetic pilot fixtures (`KIOSK-PILOT-01`, `KIOSK-PILOT-02`, `PILOT-001`, `PILOT-INACTIVE`). `attendance_dev` was kept completely untouched and isolated. **3. Defects Discovered & Fixed:** Fixed a 10px horizontal viewport blowout on screens $\le 480\text{px}$ in `public/css/style.css` (reconfigured `.kiosk-header` to vertical column layout, achieving exact 412px zero-overflow fit); updated `public/sw.js` to include `/js/kiosk-pin-attendance.js` in `PRECACHE_ASSETS` (bumped cache to `edifies-kiosk-shell-v1.4.0`); exposed `window.kioskPinAttendance` for integration testing. **4. End-to-End Pilot Execution:** Verified all 11 critical user workflows: unknown employee rejection, inactive staff rejection, wrong PIN rejection, PIN verification & Step 2 transition, mandatory photo validation (punch blocked with alert if no photo present), camera photo capture & raster conversion, successful punch confirmation (`CHECK_IN`), repeated use screen reset, check-out badge (`CHECK_OUT`), admin reports rendering with photo modal inspector, and Manager role 403 restriction. Captured 6 screenshots as proof. **5. Automated Test Suite:** Added 6 comprehensive feature tests in `tests/feature/AndroidKioskPilotIntegrationTest.php` (79 assertions). Full project test suite passed: **242 tests, 1019 assertions, 0 errors, 0 failures, ALL GREEN**. | **B. PARTIALLY VALIDATED — PHYSICAL TESTS INCOMPLETE** (Emulation Passed 100%; Physical Hardware Validation Pending Device Connection). | Owner authorized Phase 2.2: Complete Backup, Restore & Production Deployment Readiness. |
| **2026-11-06** | **Phase 2.2 — Complete Backup, Restore & Production Deployment Readiness** | Hardened PIN verification token lifecycle, executed database and photo archive backup, verified restore on disposable database, assessed shared hosting readiness, and cleaned pilot environment. **1. PIN Token Lifecycle Reconciled:** Verified server implementation enforces strict 60-second TTL (restoring specification; rejecting mistaken 10-minute report text). Hardened `KioskApiController::confirmPunch()` with explicit server-side elapsed time check (`time() - verified_at > 60`) and atomic single-use deletion. Added 5 new automated feature tests in `EmployeePinAttendanceTest.php` covering valid token, expired token, reuse after success, reuse after failure, and 60s boundary. **2. Pilot Environment Cleanup:** Confirmed `.env` is restored to `database.default.database = attendance_dev`; synthetic fixtures (`PILOT-001`, `PILOT-INACTIVE`, `KIOSK-PILOT-01`, `KIOSK-PILOT-02`) and dependent events/photos cleanly purged from `attendance_test`; `attendance_dev` and PostgreSQL untouched. **3. Initial Database & Photo Backup:** Generated MariaDB dump of `attendance_test` (`attendance_test_backup_20261106_143851.sql`, 764,370 bytes, SHA-256 `64EEA3...`) and photo archive (`attendance_photos_backup_20261106_143851.zip`, 38 files, 26,321 bytes, SHA-256 `7DE369...`). **4. Shared Hosting Readiness:** Assessed PHP 8.2+ compatibility for CodeIgniter 4.7.6, confirmed required extensions (`gd`, `fileinfo`, `mysqli`, `intl`, `mbstring`, `json`, `curl`), documented web root isolation outside `public_html`, and confirmed application can run on inexpensive cPanel shared hosting ($2 - $5/month). **5. Regression Suite:** Executed full PHPUnit test suite: **247 tests, 1,033 assertions, 0 errors, 0 failures, ALL GREEN**. | **COMPLETED & STABILIZED** (Regression passed 100%; initial backup pair noted for reconciliation in Quick Fix). | Owner authorized Quick Fix: Backup and Photo Archive Source Reconciliation. |
| **2026-11-07** | **Quick Fix — Backup and Photo Archive Source Reconciliation** | Reconciled provenance and consistency of database backup and photo archive, corrected operational instructions, and verified a 100% matched backup pair. **1. Provenance Audit:** Established that the initial Phase 2.2 backup pair (`attendance_test_backup_20261106_143851.sql` and `attendance_photos_backup_20261106_143851.zip`) was **ASYMMETRIC / INCONSISTENT**: the database was backed up with 2 active photo rows (seeded for `BKP-STAFF-01`), whereas the photo zip captured the shared `writable/attendance_photos/` directory containing 36 unreferenced orphan photos accumulated across prior automated test suites. Reconciled counts: 38 archived files, 2 DB metadata rows, 2 matching files (100% of DB), 0 missing files, 36 unreferenced orphan files (94.7% of archive). All 36 files still on disk matched the archive SHA-256 100%. **2. Correction via Isolated Matched Backup:** In an isolated test environment (`attendance_matched_test` and dedicated staging photo directory), seeded 3 staff with 5 attendance events and 5 photos (exact 1:1 parity). Generated fresh matched backups: SQL `attendance_consistent_backup_20261107_152927.sql` (29,461 bytes, SHA-256 `D5CBFA135E772F16BF2179C7731E56F18EAF6CE1387CE8A752F156B0B10EBDD7`) and ZIP `attendance_consistent_photos_20261107_152927.zip` (5 files, 3,161 bytes, SHA-256 `AEE57EE3EF9E3EC55B20A070796521EB789B8A3A880C7ED0CEFF44D2F226BB04`). **3. Full Matched Restore Verification:** Restored SQL into disposable database `attendance_restore_consistent_verify` and extracted ZIP to temporary folder. Verified: 5 DB rows, 5 archive files, 5 matched files (100%), 0 missing files (0%), 0 unreferenced orphan files (0%), 100% byte-for-byte SHA-256 hash match on all 5 files, and multi-table relational join (`photos` $\bowtie$ `events` $\bowtie$ `records` $\bowtie$ `employees`) passed 100%. Cleanly dropped disposable database and removed temp folders. **4. Operational Instruction Correction:** Removed unverified database name `edifies_attendance`. Replaced with environment-aware placeholder `<DB_NAME_FROM_ENV>` (clarifying `attendance_dev` for dev, `attendance_test` for test, and configured database for production). Preserved all existing backups and live data untouched. | **CONSISTENT BACKUP VERIFIED** (Fresh 1:1 Matched Backup Pair Verified 100%; Physical Android Device Connection Outstanding for Final Sign-Off). | **Awaiting Owner Attachment of Physical Android Device for On-Device Pilot Sign-Off (Phase 2.3).** |
| **2026-11-07** | **Master Task — Unified Architecture, Role Dispatch, Scanner Access & Git Separation** | Completed unified single-website architecture, role-based login gateway, scanner pairing gateway, project cleanup, and dedicated Git repository. **1. Architecture & Entrypoint:** One website, one CodeIgniter 4 application, and one MariaDB database serving all three access types: ADMIN (`ROLE_ADMIN`), MANAGER (`ROLE_MANAGER`), and KIOSK DEVICE (`DEVICE PRINCIPAL`). Single entrypoint at `/` opens the Central Access Gateway with Staff Sign In and Scanner Pairing card. Auto-redirects active sessions server-side (Admin $\to$ `/admin`, Manager $\to$ `/admin/employees`). Zero client-controlled roles or dropdowns. **2. Dedicated Scanner Gateway:** Implemented `/kiosk/pair` for single-use pairing codes; issues HttpOnly, `SameSite=Strict` cookie (`edifies_kiosk_token`) scoped to `/api/kiosk`; isolates scanner tokens from human sessions; redirects to `/attendance`. **3. Scanner Kiosk Interface (`/attendance`):** Hardened camera lifecycle in `kiosk-pin-attendance.js` to automatically stop camera tracks on page unload or backgrounding (`visibilitychange`); responsive layout across smartphones, tablets, and desktop webcams; explicit biometric boundary notice displayed; verified PIN attendance and photo snapshot capture remain active as primary mechanism. **4. Project Structure Cleanup:** Audited all files in `php-attendance` across 10 strict categories; purged disposable test caches and ephemeral sessions (`writable/session/*`, `build/.phpunit.cache/*`, `writable/logs/*`); preserved source, migrations, tests, `.env`, private photos, backups, and lab reference models. Added `index.html` directory listing protectors across writable directories. **5. Git Repository Separation:** Added `php-attendance/` to parent `Edifies_School/.gitignore` to eliminate untracked risk. Initialized independent Git repository in `php-attendance/`. Added airtight `.gitignore` (excluding `.env`, private photos, backups, vendor, SQL dumps). Added `.env.example` template and comprehensive `README.md` (`# Face_Detection_Software`). Completed clean pre-commit secret scan. Committed working tree on branch `main`. Configured remote `origin https://github.com/SrinuRenangi/Face_Detection_Software.git`. **6. Automated Verification:** Added 13 automated feature tests in `UnifiedGatewayAndScannerAccessTest.php`. Full regression test suite passed: **260 tests, 1092 assertions, 0 errors, 0 failures, ALL GREEN**. | **MASTER ARCHITECTURE COMPLETED & COMMITTED** (260/260 tests passing; Local Git repository clean on `main`; Remote configured). | Production deployment & physical Android sign-off ready. |

---

## 18. OPEN OWNER DECISIONS

| Decision | Current position | Approval status |
| :--- | :--- | :--- |
| **Continue PHP/CodeIgniter/MariaDB direction** | Preferred low-cost direction. Verified working and sound in audit. | **RECOMMENDED TO CONFIRM** |
| **Next Development Priority** | Complete Phase 2.3 on physical Android hardware and select affordable shared hosting. | **AWAITING OWNER SELECTION** |
| **Final kiosk device** | Inexpensive Android phone or tablet. Emulation validated (Pixel 7); backup/restore drill passed; physical device test pending attachment via USB/LAN. | **PENDING PHYSICAL DEVICE CONNECTION** |
| **Hosting Selection** | Standard cPanel shared hosting ($2 - $5/mo) with PHP 8.2+, GD, MariaDB, and Let's Encrypt SSL recommended. | **AWAITING OWNER DECISION** |
| **Face recognition** | Optional feasibility enhancement; not required to finish core attendance. | No production authorization. |
| **Human test data** | Zero human-face data currently in lab. Requires Owner Decision B.1 and Legal Decision D.1. | Not authorized for collection. |
| **Model commercial rights** | YuNet/SFace provenance carries non-commercial training data lineage. Requires IP review. | Unresolved; no production distribution. |

---

## 19. FINAL PROJECT RULE

Build the smallest reliable system that meets the school's attendance needs.

Do not optimize for architectural fashion, maximum security complexity, research-paper completeness or impressive documentation. Optimize for:
1. Correct attendance.
2. Easy admin operation.
3. Simple kiosk usage on a separate Android device.
4. Affordable deployment.
5. Reliable photos and records.
6. Straightforward backups and maintenance.
7. Safe, optional face recognition only if it proves useful.

When a simpler solution meets the requirement with lower cost and lower maintenance, choose the simpler solution and explain the trade-off.

---

## 20. UNIFIED ARCHITECTURE, ROLE-BASED LOGIN, SCANNER ACCESS & INDEPENDENT GIT REPOSITORY

### 20.1 One Application, One Website, One Database
The production attendance product consists of exactly **one deployable PHP application** and **one authoritative MariaDB database**:
- **Backend Runtime:** PHP 8.5.12
- **Framework:** CodeIgniter v4.7.6
- **Database:** MariaDB 11.4 LTS (Active development port 3307; Standard deployment port 3306)
- **Frontend Architecture:** Server-rendered HTML, vanilla CSS, vanilla JavaScript, and PWA service worker (`public/sw.js`).
- **Zero Heavy Stack Overhead:** No React, Angular, Node.js backend, microservices, Redis, Docker, or paid cloud services.

### 20.2 Three Distinct Authentication Principals
The unified backend maintains three strictly isolated authentication principals:

```mermaid
flowchart TD
    Gateway["Central Access Gateway (GET /)"] --> AuthDecision{"Credential Evaluation"}
    AuthDecision -->|Human Admin Credential| AdminSession["ROLE_ADMIN Session"]
    AuthDecision -->|Human Manager Credential| ManagerSession["ROLE_MANAGER Session"]
    AuthDecision -->|Device Cookie (edifies_kiosk_token)| ScannerSession["DEVICE PRINCIPAL (Kiosk)"]
    AuthDecision -->|Pairing Code (POST /kiosk/pair)| PairWorkflow["Device Registration & Cookie Issuance"]
    
    AdminSession --> AdminApp["Admin Application (/admin)"]
    ManagerSession --> ManagerApp["Manager Application (/admin/employees)"]
    ScannerSession --> ScannerApp["Attendance Scanner (/attendance)"]
    PairWorkflow --> ScannerApp
```

1. **ADMIN (`ROLE_ADMIN`):**
   - Authenticated via human administrative username & password (`/login` or `/admin/login`).
   - Automatically dispatched to `/admin` dashboard.
   - Capabilities: Complete Staff Directory management, attendance history & reports, photo inspection, attendance review queue, kiosk device registration, one-time pairing code issuance, and device revocation.
2. **MANAGER (`ROLE_MANAGER`):**
   - Authenticated via human manager credentials (`/login`).
   - Automatically dispatched to approved landing page `/admin/employees`.
   - Capabilities: Read-only Staff Directory viewing, launching the Attendance Kiosk.
   - Strict Boundaries: Prohibited from reports, time corrections, employee mutations, review queue, and device pairing (returns HTTP 403 Forbidden).
3. **KIOSK DEVICE (`DEVICE PRINCIPAL`):**
   - Registered physical scanner terminal (Android smartphone, Android tablet, laptop, or desktop with webcam).
   - Paired using an Admin-generated 128-bit single-use pairing code (`/kiosk/pair`).
   - Authorized via a persistent, cryptographically secure HttpOnly cookie (`edifies_kiosk_token`, `SameSite=Strict`, path `/api/kiosk`).
   - Completely isolated from human sessions (a scanner cookie cannot access `/admin`, and human sessions cannot call device-only endpoints without a paired cookie).

### 20.3 Route Dispatch & Access Gateway Architecture

| URL Route | Methods | Permitted Principal | Behavior & Redirection |
| :--- | :--- | :--- | :--- |
| `/` | `GET` | Public / Gateway | **Central Access Gateway:** Shows Staff Sign In & Scanner Pairing card. If an Admin session is active, auto-redirects to `/admin`; if Manager session is active, auto-redirects to `/admin/employees`. |
| `/login` | `GET`, `POST` | Public / Humans | **Human Staff Authentication:** Validates username & password. Dispatches `ROLE_ADMIN` $\to$ `/admin`, `ROLE_MANAGER` $\to$ `/admin/employees`. Aliased to `/admin/login` for backward-compatibility. |
| `/admin/*` | `GET`, `POST` | `ROLE_ADMIN` | **Admin Application:** Protected by `AdminAuthFilter:ADMIN,ROLE_ADMIN`. Managers strictly receive HTTP 403 Forbidden. |
| `/admin/employees` | `GET` | `ROLE_ADMIN`, `ROLE_MANAGER` | **Staff Directory:** Manager has read-only view with dashboard links hidden; Admin has full mutation capabilities. |
| `/kiosk/pair` | `GET`, `POST` | Device Registrar | **Scanner Pairing Gateway:** Exchanges single-use 32-character pairing code for high-entropy device token cookie (`edifies_kiosk_token`) and redirects to `/attendance`. Rate-limited (5/min). |
| `/attendance` | `GET` | Scanner / Staff | **Dedicated Attendance Scanner:** Responsive touchscreen interface with live camera preview, staff status indicator, and verified 6-digit PIN attendance fallback. |

### 20.4 Camera Lifecycle & Biometric Integration Boundary
- **Camera Startup & Permissions:**
  - Front-facing camera preferred on mobile (`facingMode: 'user'`); default webcam used on laptops/desktops.
  - Automatic permission request with friendly fallback if hardware is missing or access is denied.
  - Hardware camera tracks automatically stopped upon photo confirmation, cancellation, tab unload (`beforeunload`), or tab backgrounding (`visibilitychange`).
- **Face Recognition Integration Boundary:**
  - Biometric recognition engine is staged behind a strict feature-enable boundary and **disabled in production** pending physical device validation and explicit owner acceptance.
  - Zero auto-enrollment; zero biometric template creation without owner authorization.
  - Verified Staff PIN attendance with real-time photo audit capture remains the active, authoritative attendance mechanism.

### 20.5 Project Structure Cleanup & Classification
Comprehensive source and runtime inspection of `C:\Users\sriva\OneDrive\Desktop\Edifies_School\php-attendance` established the following classification:
1. **Essential Source Code:** `app/Controllers/*` (including new `KioskPairingController.php`), `app/Models/*`, `app/Services/*`, `app/Filters/*`, `app/Views/*`, `app/Common.php`, `spark`.
2. **Required Dependencies:** `vendor/` (CodeIgniter 4 framework, PHPUnit), `composer.json`, `composer.lock`.
3. **Configuration & Migrations:** `app/Config/*`, `app/Database/Migrations/*` (10 migrations), `.env` (live config), `.env.example` (clean template).
4. **Required Assets:** `public/css/style.css`, `public/js/*` (`edifies-multidevice-kiosk.js`, `kiosk-pin-attendance.js`, `kiosk-clock.js`, `face-api.js`), `manifest.json`, `sw.js`, `favicon.ico`.
5. **Tests & Fixtures:** `tests/unit/*`, `tests/feature/*` (including new `UnifiedGatewayAndScannerAccessTest.php`), `tests/database/*`, `tests/concurrency/*`, `phpunit.dist.xml`.
6. **Disposable Generated Artifacts (Purged):** `build/.phpunit.cache/*`, `build/logs/*`, `writable/cache/*`, `writable/debugbar/*`.
7. **Disposable Ephemeral Data (Purged):** `writable/session/*` (old session files), `writable/logs/*`.
8. **Sensitive / Private Files (Preserved on disk, Excluded from Git):** `.env` (contains database credentials and encryption key), `writable/attendance_photos/*` (staff photographs).
9. **Uncertain / Historical Files (Preserved):** `writable/dev_models/*` (face research reference models), `writable/sync_test_schema.sql`, `builds` (CI4 release toggle).
10. **Directory Listing Protection:** Added `index.html` (403 Forbidden) across `build/`, `writable/attendance_photos/`, `writable/backups/`, and `writable/dev_models/`.

### 20.6 Git Repository Separation
To eliminate the vulnerability of running untracked inside the legacy Java repository:
1. Added `php-attendance/` to parent `C:\Users\sriva\OneDrive\Desktop\Edifies_School\.gitignore`.
2. Initialized independent Git repository directly inside `C:\Users\sriva\OneDrive\Desktop\Edifies_School\php-attendance`.
3. Established airtight `.gitignore` excluding `.env`, private photos, backups, `vendor/`, `build/`, and SQL dumps.
4. Created sanitized `.env.example` and `# Face_Detection_Software` `README.md`.
5. Verified 0 secrets, 0 employee photos, and 0 Java files staged.
6. Created initial commit on branch `main`.
7. Configured verified remote `origin https://github.com/SrinuRenangi/Face_Detection_Software.git`.

### 20.7 Verification & Test Results
- Added 13 new automated feature tests in `tests/feature/UnifiedGatewayAndScannerAccessTest.php`.
- Complete PHPUnit regression suite executed: **260 tests, 1092 assertions, 0 errors, 0 failures, ALL GREEN**.

### 20.8 Git Pre-Push Verification & Sanity Audit Status
A thorough pre-push sanity audit of `C:\Users\sriva\OneDrive\Desktop\Edifies_School\php-attendance` was completed on 2026-11-07:

1. **Committed File List Inspection:**
   - Initial commit `6314cb7` tracked 192 files comprising pure PHP/CodeIgniter 4 application assets.
   - Follow-up clean commit `482b045` updated the project title.
2. **Exclusion of Sensitive & Private Data:**
   - `.env` is strictly uncommitted and protected by `.gitignore`.
   - Private attendance photographs (`writable/attendance_photos/*.jpg`) are completely excluded.
   - SQL dumps (`*.sql`), backup archives (`writable/backups/`), session files (`writable/session/`), and logs (`writable/logs/`) are completely excluded.
   - Only dummy `index.html` directory listing guards are tracked in sensitive writable folders.
3. **Environment Template Sanitization:**
   - `.env.example` verified to contain dummy placeholders only (`ChangeMeToStrongPassword`, zero real encryption keys).
4. **Third-Party & Build Exclusion:**
   - `vendor/`, `.phpunit.cache/`, and cache directories verified 100% excluded.
5. **No Cross-Contamination with Java Stack:**
   - Zero Java files (`pom.xml`, `.java`, `mvnw`, `frontend/`, `target/`) exist in the PHP tree.
6. **Parent Repository Isolation:**
   - Parent repository `C:\Users\sriva\OneDrive\Desktop\Edifies_School\.gitignore` ignores `php-attendance/`.
   - Confirmed via `git check-ignore php-attendance/`.
7. **README Project Title Updated:**
   - Corrected line 1 from `# Face_Detection_Software` to `# Edifies School Attendance Management System`.
8. **Clean Working Tree & Branch Status:**
   - Branch: `main`.
   - Initial commit `6314cb7` preserved without history rewriting.
   - Working tree: clean (`nothing to commit, working tree clean`).
9. **Configured Remote Inspection:**
   - Configured remote: `origin https://github.com/SrinuRenangi/Face_Detection_Software.git`.
   - Verified reachable via `git ls-remote origin` (exit code 0; remote repository is currently empty).
10. **Remote Identity & Owner Confirmation:**
   - The remote repository is `https://github.com/SrinuRenangi/Face_Detection_Software.git`.
   - The Project Owner confirmed that this repository is intended for the PHP face recognition and attendance marker application.

### 20.9 Deletion of Legacy Java Code & Project Cleanup
On 2026-11-07, per explicit Project Owner instruction:
1. **Deleted Legacy Java Application:**
   - Removed `src/` (Spring Boot controllers, services, repositories, tests).
   - Removed `pom.xml`, `mvnw`, `mvnw.cmd`.
   - Removed `frontend/` (legacy Angular application).
   - Removed `target/` (build directory).
   - Removed `pgdata/` (PostgreSQL local database).
   - Removed `photos/`, `photos_recovery/`, `backups/`, `scripts/`, `run_app.ps1`.
   - Removed `Dockerfile`, `docker-compose.yml`, `.dockerignore`.
2. **Purged Obsolete Markdown Reports & Non-Project Assets:**
   - Removed all intermediate `stage_3_*.md` files (16 files).
   - Removed `docs/` (obsolete Angular, Spring Boot, and PostgreSQL design specs).
   - Removed `tools/` (external cloned repositories: graphify, ponytail).
   - Removed `php-attendance/writable/sync_test_schema.sql` (temporary test fixture).
3. **Clean Active Workspace:**
   - The workspace root `C:\Users\sriva\OneDrive\Desktop\Edifies_School` now contains exclusively:
     - `php-attendance/` — The complete active PHP attendance application.
     - `EDIFIES_ATTENDANCE_MASTER_ARCHITECTURE_AND_WORK_GUIDE.md` — Single master document.
     - `.agents/` — Antigravity agent configuration.
     - `.gitignore` — Ignore rules for root repository.

### 20.10 Minimalistic Portal UI & Removal of Diagnostic Box
On 2026-11-07:
1. **Redesigned Default Gateway (`/`):**
   - Replaced cluttered developer layout with a clean, centered, modern SaaS card.
   - Refined typography with Edifies School logo icon, clear username/password inputs, and primary "Staff Sign In" action.
   - Provided a secondary action for pairing attendance scanner terminals.
2. **Removed Developer Diagnostic Box:**
   - Completely eliminated the visible "Backend Foundation Status" card from user view.
   - Kept diagnostic variables in the background for automated monitoring without polluting the UI.
   - Fixed MariaDB connection inspection to use `$db->connID`.

### 20.11 Automated Touchless Face Recognition & Minimalist Pairing
On 2026-11-07:
1. **Scanner Viewport Overhauled (`/attendance`):**
   - Completely removed PIN entry keypad, employee ID input textboxes, and staging warnings.
   - Centered high-tech camera viewport with biometric scanning reticle, live clock, status pill, and celebratory punch popup.
   - Integrated client-side `face-api.js` neural models (`tinyFaceDetector`, `faceLandmark68Net`, `faceRecognitionNet`) loaded from `/models`.
   - Real-time touchless matching ($>85\%$ confidence) against active templates with 60-second duplicate cooldown.
   - 25-second ambient sleep screen to conserve tablet battery, waking instantly on face approach or screen touch.
2. **Streamlined Device Pairing (`/kiosk/pair`):**
   - Centered, fully responsive card layout for mobile, tablet, laptop, and desktop.
   - Removed all unnecessary inputs (Device Label, Device Type, and external login links).
   - Single input: **Single-Use Pairing Code**.
   - If valid: sets secure HttpOnly cookie and immediately opens 24/7 scanning interface. If invalid: displays clean error alert.
3. **Backend Face Template Architecture:**
   - Added `EmployeeFaceTemplateModel.php` for `employee_face_templates` table.
   - Added `GET /api/kiosk/face-templates` to provide enrolled embeddings to paired kiosks.
   - Added `POST /api/kiosk/face-punch` for automated attendance recording with photo audit capture.
   - Added `POST /api/admin/employees/(:segment)/face-template` to register staff templates.
   - Added "Enroll Face" button in Staff Directory.



