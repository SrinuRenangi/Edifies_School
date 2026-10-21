# Security Architecture Specification
## Staff Attendance Management System — Phase 3 Technical Specification

**Document Version:** 1.0.0  
**Status:** Approved Security Baseline  

---

### 1. Dual-Track Authentication Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        AUTHENTICATION TAXONOMY                         │
├───────────────────────────────────┬────────────────────────────────────┤
│       TRACK A: ADMIN CONSOLE      │       TRACK B: ATTENDANCE KIOSK    │
├───────────────────────────────────┼────────────────────────────────────┤
│ - Actor: HR / System Administrator│ - Actor: Staff Members on Kiosk    │
│ - Identity: Username + Password   │ - Identity: Device Token + ID + PIN│
│ - Hash: Argon2id (64MB, 3 iter)   │ - Hash: BCrypt (Cost Factor 12)    │
│ - Session: Short-lived JWT (15m)  │ - Session: Ephemeral Token (60s)   │
│ - Storage: HttpOnly Secure Cookie │ - Storage: In-Memory Signal Only   │
│ - CSRF: SameSite=Strict + Header  │ - Device Trust: Paired Device Token│
└───────────────────────────────────┴────────────────────────────────────┘
```

---

### 2. Administrator Authentication & Session Governance
1. **Password Storage:** Encrypted using **Argon2id** with salt. Plaintext passwords are never held in memory beyond authentication execution.
2. **JWT Cookie Strategy:**
   - Access token issued inside an `HttpOnly`, `Secure`, `SameSite=Strict` cookie (`__Host-jwt-token`).
   - Prevents JavaScript access, neutralizing Cross-Site Scripting (XSS) token theft.
   - Access token lifespan: **15 minutes**.
   - Refresh token rotation: Persisted in database, one-time use, with automatic invalidation upon reuse detection.
3. **Brute-Force Login Defense:**
   - Max 5 failed login attempts per username/IP within 10 minutes triggers an exponential backoff lock.

---

### 3. Kiosk Security Model & Shared Device Protection
1. **Cryptographic Kiosk Pairing:**
   - Physical kiosk tablets/browsers are paired by an administrator using a one-time cryptographic pairing token.
   - Successful pairing provisions an immutable `kiosk_device_token` stored in browser `localStorage`.
   - All subsequent kiosk API requests must supply header: `X-Kiosk-Device-Token: <Token>`.
2. **Employee PIN Security:**
   - PINs (4-6 digits) are hashed using salted **BCrypt (cost factor 12)**.
   - Successful PIN validation generates an **ephemeral 60-second punch ticket** (`kiosk_punch_ticket`) scoped strictly to that employee ID.
   - **Rate Limiting:** 5 incorrect PIN attempts within 15 minutes initiates an automated 15-minute lockout on the employee ID.
3. **In-Memory Sanitization:**
   - The Angular frontend never persists employee PINs, tokens, or names in `localStorage` or `sessionStorage`.
   - All state is maintained in reactive Angular Signals and cleared via `KioskStateService.reset()` upon punch completion or 15-second inactivity timeout.

---

### 4. Photo Privacy & Access Control
- **Non-Public Web Directory:** Photos are saved on a local filesystem volume (`/var/data/attendance/photos/`) completely outside Nginx's static file serving paths.
- **Authenticated Photo Streaming:**
  - Endpoint: `GET /api/v1/photos/{photoId}`
  - Access is restricted exclusively to authenticated users possessing role `HR_ADMIN` or `SUPER_ADMIN`.
  - The endpoint streams image bytes directly from disk with headers `Content-Disposition: inline` and `Cache-Control: private, max-age=3600`.
  - Unauthenticated requests receive HTTP 401 Unauthorized; kiosk clients receive HTTP 403 Forbidden.

---

### 5. Secrets Management & Sanitization Invariants
1. **Externalized Secrets:** All sensitive credentials (`DB_PASSWORD`, `JWT_SECRET`, `KIOSK_MASTER_KEY`) are injected via OS environment variables. Zero credentials are committed to version control.
2. **Log Redaction:** A dedicated Logback filter scrubs any keys matching `pin`, `password`, `token`, `secret`, or `photo` from standard output and log files.
