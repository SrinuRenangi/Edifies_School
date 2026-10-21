# Backup, Recovery, and Disaster Recovery Design
## Staff Attendance Management System — Phase 3 Technical Specification

**Document Version:** 1.0.0  
**Status:** Approved Backup Baseline  
**Target RPO (Recovery Point Objective):** $\le$ 24 Hours (Nightly Automated Dump)  
**Target RTO (Recovery Time Objective):** $\le$ 1 Hour (Container Spin-up & DB Restore)  

---

### 1. Backup Scope & Architectural Taxonomy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        DATA BACKUP TAXONOMY                            │
├───────────────────────────────────┬────────────────────────────────────┤
│   A. STRUCTURED RELATIONAL DATA   │     B. UNSTRUCTURED PHOTO ASSETS   │
├───────────────────────────────────┼────────────────────────────────────┤
│ - Scope: PostgreSQL 16 Database   │ - Scope: /var/data/photos/ Volume  │
│ - Tool: pg_dump (Custom Format -Fc)│ - Tool: Incremental Tar / Rsync   │
│ - Frequency: Daily at 02:00 AM IST│ - Frequency: Daily at 02:30 AM IST │
│ - Compression: Gzip Level 6       │ - Compression: Tar with Gzip       │
│ - Encryption: GPG AES-256         │ - Encryption: GPG AES-256         │
│ - Retention: 30 Daily, 12 Monthly,│ - Retention: Aligned with Photo    │
│   3 Yearly Archives               │   Retention Policy (90-180 Days)   │
└───────────────────────────────────┴────────────────────────────────────┘
```

---

### 2. Automated Backup Execution Pipeline
A lightweight backup cron container or host script (`docker/scripts/backup.sh`) executes nightly:

```bash
#!/usr/bin/env bash
set -euo pipefail

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/var/backups/attendance"
mkdir -p "${BACKUP_DIR}"

echo "[${TIMESTAMP}] Initiating PostgreSQL backup..."
docker exec -t attendance_postgres pg_dump -U postgres -d attendance -Fc -Z 6 > \
  "${BACKUP_DIR}/db_${TIMESTAMP}.dump"

echo "[${TIMESTAMP}] Initiating photo storage backup..."
tar -czf "${BACKUP_DIR}/photos_${TIMESTAMP}.tar.gz" -C /var/data/attendance/photos .

echo "[${TIMESTAMP}] Encrypting archives with GPG..."
gpg --symmetric --batch --passphrase "${BACKUP_ENCRYPTION_KEY}" \
  "${BACKUP_DIR}/db_${TIMESTAMP}.dump"
gpg --symmetric --batch --passphrase "${BACKUP_ENCRYPTION_KEY}" \
  "${BACKUP_DIR}/photos_${TIMESTAMP}.tar.gz"

rm -f "${BACKUP_DIR}/db_${TIMESTAMP}.dump" "${BACKUP_DIR}/photos_${TIMESTAMP}.tar.gz"

echo "[${TIMESTAMP}] Pruning archives older than 30 days..."
find "${BACKUP_DIR}" -name "*.gpg" -mtime +30 -exec rm {} \;
echo "[${TIMESTAMP}] Backup completed successfully."
```

---

### 3. Database vs. Photo Consistency During Recovery
Because the database and photo storage backups run sequentially, there is a theoretical 30-minute window between DB dump and photo snapshot.
- **Authoritative Rule:** The PostgreSQL database is the single authoritative source of truth.
- **Missing Photo Graceful Fallback:** If a photo referenced in the restored database is missing from the photo archive (e.g., recorded during the 30-minute gap before a catastrophic host crash), the application handles this gracefully. The photo streaming endpoint returns a placeholder badge: `"Photo unavailable due to recovery"`. The text attendance punch, timestamps, and status remain 100% valid.

---

### 4. Disaster Recovery Scenarios & Procedures

| Failure Scenario | Recovery Procedure | Target RTO |
| :--- | :--- | :---: |
| **Accidental Data Modification** | Administrator review of `audit_logs` table reveals exact previous state. Admin issues corrective manual adjustment with audit reason. | < 5 mins |
| **Database Corruption / Disk Defect** | Stop PostgreSQL container $\rightarrow$ wipe damaged `postgres_data` volume $\rightarrow$ decrypt latest `.dump.gpg` $\rightarrow$ run `pg_restore -d attendance db_latest.dump` $\rightarrow$ restart app. | < 20 mins |
| **Complete Host Hardware Failure** | Provision new server $\rightarrow$ clone Git repository $\rightarrow$ restore `.env.production` $\rightarrow$ pull latest backups $\rightarrow$ execute `restore.sh` $\rightarrow$ launch via `docker compose up -d`. | < 45 mins |
| **Application Deployment Regression** | Fast rollback: Update `docker-compose.yml` to reference previous stable Docker image tag (e.g., `image: attendance-app:1.0.1` $\rightarrow$ `1.0.0`) and run `docker compose up -d app`. | < 2 mins |

---

### 5. Quarterly Restoration Drill
A backup that has never been restored is not a valid backup.
- **Requirement:** Every 90 days, an automated CI/CD pipeline or operator script spins up an isolated disposable PostgreSQL container, restores the latest backup dump, verifies row counts against `SELECT COUNT(*) FROM attendance_sessions`, and outputs a signed verification log.
