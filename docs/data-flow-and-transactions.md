# Attendance Data Flow & Transaction Boundary Design
## Staff Attendance Management System — Phase 3 Technical Specification

**Document Version:** 1.0.0  
**Status:** Approved Transactional Specification  

---

### 1. The Distributed Atomicity Challenge
In a system combining a relational database (PostgreSQL 16) and a filesystem/object store (Photo Storage), atomic commits cannot be guaranteed across both storage engines via standard two-phase commit (XA) without unacceptable performance degradation.

#### Evaluation of Consistency Approaches

| Strategy | Description | Strengths | Weaknesses | Architectural Verdict |
| :--- | :--- | :--- | :--- | :--- |
| **Approach A: Upload First, Commit DB Reference** | 1. Write photo to disk under UUID.<br>2. Execute DB transaction.<br>3. If DB fails, delete file in rollback. | Fast, clean transactional boundary in DB. Zero partial rows in PostgreSQL. | Orphaned file if server crashes precisely between disk write and DB commit. | **SELECTED & APPROVED** (with automated orphan file cleanup worker). |
| **Approach B: Two-Phase Staged Upload** | 1. Write file to `/tmp`.<br>2. Insert DB record with `STAGE` status.<br>3. Move file to permanent location.<br>4. Update DB to `ACTIVE`. | Eliminates unreferenced permanent files. | Complex multi-step state machine; doubles I/O writes; latency overhead. | Rejected (over-engineered for 20-50 users). |
| **Approach C: Store Base64 in PostgreSQL** | Store image bytes directly inside PostgreSQL `BYTEA` column. | Single atomic database transaction. | Bloats PostgreSQL WAL, backup dumps, buffer pool; degrades historical query speed. | Rejected (violates enterprise scalability guidelines). |

---

### 2. Selected Strategy: Approach A with Spring Transaction Synchronization

```mermaid
sequenceDiagram
    autonumber
    participant Kiosk as Angular Kiosk
    participant Filter as Idempotency Filter
    participant Service as AttendanceService
    participant Storage as PhotoStorageService
    participant DB as PostgreSQL 16 (HikariCP)
    participant Sync as TransactionSynchronizationManager

    Kiosk->>Filter: POST /api/v1/kiosk/punch (Payload + X-Idempotency-Key)
    Filter->>DB: Query IdempotencyRecord by Key
    alt Key already exists and COMMITTED
        DB-->>Filter: Return previous cached response
        Filter-->>Kiosk: 200/201 (Cached Replay)
    else Key in-flight
        Filter-->>Kiosk: 409 Conflict ("Transaction in progress")
    else Key is new
        Filter->>Service: Proceed with punch execution
    end

    Service->>Storage: saveImage(imageBytes) ➔ saves to /photos/YYYY/MM/<uuid>.jpg
    Storage-->>Service: photoStorageKey

    Service->>Sync: registerSynchronization (afterRollback: delete file)

    rect rgb(230, 245, 255)
        Note over Service, DB: @Transactional Execution Boundary
        Service->>DB: INSERT INTO attendance_photos (id, storage_path, hash)
        Service->>DB: INSERT/UPDATE attendance_sessions (work_date, in_time, photo_id)
        Service->>DB: INSERT INTO idempotency_records (key, response_body)
        Service->>DB: INSERT INTO audit_logs (action, actor, timestamp)
        Service->>DB: COMMIT TRANSACTION
    end

    alt DB Commit Succeeds
        Sync->>Sync: Commit confirmed; file kept permanently
        Service-->>Kiosk: 201 Created / 200 OK
    else DB Commit Fails / Constraint Violation
        DB-->>Service: Rollback Transaction
        Sync->>Storage: execute afterRollback() ➔ deleteFile(photoStorageKey)
        Service-->>Kiosk: 500 Error / 409 Conflict (Zero partial data)
    end
```

---

### 3. Detailed Step-by-Step Punch Execution Pipeline

1. **Step 1: Idempotency Key Gatekeeper:**
   - Every punch request must supply header `X-Idempotency-Key: <UUIDv4>`.
   - The system checks `idempotency_records`. If found with status `SUCCESS`, the original response is replayed immediately. If found with status `PENDING`, HTTP 409 Conflict is returned to prevent duplicate concurrent processing.
2. **Step 2: Photo Validation & Storage:**
   - Magic bytes inspected (`0xFF 0xD8 0xFF` for JPEG; `0x89 0x50 0x4E` for PNG).
   - EXIF metadata stripped via Java image pipeline.
   - File written to disk: `/var/data/attendance/photos/2026/10/<random-uuid>.jpg`.
3. **Step 3: Transaction Synchronization Hook:**
   - A Spring `TransactionSynchronization` hook is registered:
     ```java
     TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
         @Override
         public void afterCompletion(int status) {
             if (status == STATUS_ROLLED_BACK) {
                 photoStorageService.deleteImage(photoStorageKey);
             }
         }
     });
     ```
4. **Step 4: Authoritative Database Transaction (`@Transactional`):**
   - Check-In: Inserts new `attendance_sessions` with `in_time = CURRENT_TIMESTAMP`, `status = 'INCOMPLETE'`.
   - Check-Out: Updates existing session with `out_time = CURRENT_TIMESTAMP`, `status = 'PRESENT'`, checking `@Version` for optimistic concurrency.
   - Saves `idempotency_records` row containing cached JSON response.
   - Flushes transaction to PostgreSQL WAL.
5. **Step 5: Client Response:**
   - Kiosk receives authoritative 201/200 response and triggers green confirmation banner.

---

### 4. Orphaned Image Cleanup Worker (Fail-Safe Recovery)
If the host server experiences an abrupt power loss after Step 2 but before Step 3/4 completes, an orphaned file could theoretically remain on disk without a database reference.
- **Worker:** A scheduled maintenance task runs weekly:
  ```java
  @Scheduled(cron = "0 0 3 * * SUN") // Every Sunday at 03:00 AM IST
  public void purgeOrphanedPhotos() {
      // 1. Scan storage files older than 24 hours.
      // 2. Query database for existence of storage_path.
      // 3. Delete files having zero database references.
  }
  ```
- **Result:** Complete self-healing consistency without distributed locks or external message brokers.
