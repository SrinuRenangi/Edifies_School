# Edifies School Attendance System - Operations & Administration Guide

This runbook contains all critical operational commands, default credentials, database reset procedures, kiosk configuration, and architecture details for the **Edifies School Touchless Attendance Platform**.

---

## 1. Quick Start & How to Run the System

### Prerequisites
- **PHP:** Version 8.2 or higher (located at `C:\tools\php85\php.exe`) with `ext-pdo_mysql`, `ext-mbstring`, `ext-intl`, `ext-curl`.
- **Database:** MariaDB 11.4+ running on port `3307`.
  - Database name: `attendance_dev` (development) / `attendance_test` (automated test harness).
  - Database user: `root` (no password / empty password by default).

### Starting the Web Server
Open PowerShell or your terminal in the `php-attendance` directory and run:

```powershell
# Navigate to the CodeIgniter project directory
cd c:\Users\sriva\OneDrive\Desktop\Edifies_School\php-attendance

# Start the local development server on port 8088
& "C:\tools\php85\php.exe" spark serve --port 8088
```
*(Or simply `php spark serve --port 8088` if PHP is in your system PATH).*

### Application URLs
| Service / Page | URL | Description |
| :--- | :--- | :--- |
| **Unified Sign-In** | `http://127.0.0.1:8088/admin/login` | Single entry point for Super-Admin, Admin, Manager, Scanner |
| **Touchless Attendance Kiosk** | `http://127.0.0.1:8088/attendance` | Fullscreen facial recognition kiosk for staff check-in/out |
| **Kiosk Device Pairing** | `http://127.0.0.1:8088/kiosk/pair` | One-time code terminal pairing screen |
| **Super-Admin Control Panel** | `http://127.0.0.1:8088/developer/control` | Emergency kill-switch, database controls, account management |
| **Admin Dashboard** | `http://127.0.0.1:8088/admin/dashboard` | School administration, reports, staff management |
| **Manager Dashboard** | `http://127.0.0.1:8088/manager/dashboard` | Shift roster, attendance logs, team oversight |
| **User Settings** | `http://127.0.0.1:8088/admin/settings` | Self-service password and credential updates |

---

## 2. Default System Roles & Credentials

All passwords in the system are encrypted using standard **Bcrypt** (`PASSWORD_BCRYPT`). Zero plaintext passwords exist in the codebase.

| Role | Username | Email | Default Password | Dashboard / Landing Route |
| :--- | :--- | :--- | :--- | :--- |
| **SUPER_ADMIN** | `superadmin` | `developer@edifies.school` | `SuperAdmin@2026!` | `/developer/control` |
| **ADMIN** | `admin` | `admin@edifies.school` | `Admin@2026!` | `/admin/dashboard` |
| **MANAGER** | `manager` | `manager@edifies.school` | `Manager@2026!` | `/manager/dashboard` |
| **SCANNER** | `scanner` | `scanner@edifies.school` | `Scanner@2026!` | `/scanner` |

> [!NOTE]
> Default passwords can be customized in the `.env` file via `INITIAL_SUPERADMIN_PASSWORD`, `INITIAL_ADMIN_PASSWORD`, `INITIAL_MANAGER_PASSWORD`, and `INITIAL_SCANNER_PASSWORD`.

### Updating Your Password
1. Sign in to any account via `http://127.0.0.1:8088/admin/login`.
2. Click **Settings** in the top navigation bar (`/admin/settings`).
3. Enter your current password, type your new password (minimum 8 characters), and confirm.
4. Upon successful update, active sessions are rotated and stale tokens are invalidated.

### Browser Navigation & Anti-Caching Protection
- All administrative and kiosk routes enforce strict `Cache-Control: no-store, no-cache, must-revalidate, max-age=0` headers.
- When a user logs out, the session is completely destroyed on the server and session cookies are cleared.
- Clicking the browser's **Back** or **Forward** arrows after logging out will automatically force an immediate refresh to the login screen, preventing back-forward cache (`bfcache`) data leaks.

---

## 3. Database Reset & Data Wipe Commands

When you want to reset your database for real-world testing or wipe all mock/seed data, use the built-in Spark CLI tool:

### Option A: Complete Database Wipe (Clean Slate)
To purge **everything**—all employees, enrolled face biometric templates, attendance ledger events, daily summaries, stored photo files, pairing tokens, audit logs, and devices:

```powershell
# Navigate to the php-attendance directory
cd c:\Users\sriva\OneDrive\Desktop\Edifies_School\php-attendance

# Complete wipe command
& "C:\tools\php85\php.exe" spark db:reset-records --all
```

**What `--all` does:**
- Truncates `employees` and `employee_face_templates`.
- Truncates `attendance_events`, `attendance_records`, and `attendance_photos`.
- Truncates `attendance_corrections`, `attendance_review_decisions`, and `idempotency_keys`.
- Truncates `kiosk_pairing_codes`, `kiosk_time_anchors`, and `kiosk_devices`.
- Truncates `admin_audit_logs`.
- Deletes physical captured `.jpg` files from disk inside `writable/attendance_photos/`.
- Resets the emergency system killswitch to `0` (ACTIVE).
- Automatically ensures the 4 pristine administrative accounts (`superadmin`, `admin`, `manager`, `scanner`) exist and are ready for login.

### Option B: Transactional Attendance Reset (Keep Staff & Biometrics)
If you already registered your staff members and enrolled their faces, but want to wipe attendance punch history, photo captures, and daily records:

```powershell
& "C:\tools\php85\php.exe" spark db:reset-records
```

**What standard reset does:**
- Preserves all employee records and biometric face templates.
- Truncates attendance history and daily aggregates.
- Purges temporary pairing codes and stored attendance photos.

---

## 4. 100% Touchless Facial Attendance Architecture

All PIN fallbacks, PIN prompts, and keypad modals have been completely removed from the platform. The kiosk operates entirely touchless.

### Facial Recognition Rules & Thresholds
1. **Mandatory 85% Confidence Threshold:**
   - Any facial detection with similarity/confidence $< 85\%$ ($0.85$) is strictly rejected as `"Unrecognized Face"`.
   - The system **never** guesses or defaults to the closest staff member.
2. **Biometric Pre-filtering & Gender Segregation:**
   - Pre-filters candidates by demographic metadata (e.g., Male profiles are never matched against Female profiles and vice versa).
   - Prevents misidentifications between distinct staff members.
3. **Audit Photo Logging:**
   - Raw face crops, confidence scores, and vector distances are permanently logged in `attendance_photos` and `attendance_events`.
4. **Persistent Cooldown Protection:**
   - Once a staff member is verified, an automatic 60-second cooldown is enforced.
   - Subsequent scans within 60s inform the user without re-triggering check-in requests, green animations, or camera re-initializations.

---

## 5. Staff Enrollment & Kiosk Pairing Workflow

### How to Enroll a New Staff Member
1. Sign in as `admin` at `http://127.0.0.1:8088/admin/login`.
2. Go to **Staff Directory** (`/admin/employees`) and click **+ Enroll Employee**.
3. Fill in the employee's details:
   - **Employee ID:** e.g., `EMP-001`
   - **Full Name:** Staff member's official name
   - **Gender:** `Male`, `Female`, or `Other` *(essential for biometric partition matching)*
   - **Department / Designation:** Role information
   - **Biometric Photo:** Upload a clear passport-style photo or capture using the webcam.
4. Save the profile. The system computes and stores the 128-dimensional facial embedding vector.

### How Kiosk Pairing Works
1. From the Admin Dashboard, navigate to **Kiosk Devices** (`/admin/kiosks`).
2. Click **Generate 1-Time Pairing Code**. You will receive a 6-digit code (valid for 15 minutes).
3. On the dedicated kiosk tablet, laptop, or terminal, open:
   `http://127.0.0.1:8088/kiosk/pair`
4. Enter the 6-digit pairing code and submit.
5. The terminal receives its permanent cryptographic UUID and pairing token, stored securely in the browser's `localStorage`.
6. From then on, visiting `http://127.0.0.1:8088/attendance` on that terminal immediately launches the touchless attendance scanner without asking for pairing again.

---

## 6. Developer Emergency Kill-Switch

The Developer Control Dashboard (`/developer/control`) provides an emergency master switch:
- **ACTIVE (ON):** Normal school operations. Admin, Manager, and Scanner portals accept logins and process attendance.
- **SUSPENDED (OFF):** Instantly locks out all Admin, Manager, and Scanner routes. Any visitor receives a clean maintenance screen:
  > *"Application access is temporarily suspended. Please contact the system provider."*
- **Fail-Safe:** Only the `SUPER_ADMIN` login and `/developer/control` remain active when the kill-switch is engaged, allowing developers to perform maintenance, migrations, or account recovery.

---

## 7. Running the Automated Test Suite

To verify system integrity, run the complete PHPUnit test suite:

```powershell
cd c:\Users\sriva\OneDrive\Desktop\Edifies_School\php-attendance
& "C:\tools\php85\php.exe" vendor/bin/phpunit --no-coverage
```

- **Current Status:** 252 tests, 1,067 assertions passing with **0 failures, 0 errors**.

