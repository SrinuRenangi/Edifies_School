# Deployment Architecture Specification
## Staff Attendance Management System — Phase 3 Technical Specification

**Document Version:** 1.0.0  
**Status:** Approved Deployment Architecture  
**Container Engine:** Docker 29+ / Docker Compose v2  

---

### 1. Conceptual Topologies

#### 1.1 Local Development Topology
Designed for rapid developer feedback and zero-configuration environment setup:
- **`postgres` container:** Runs PostgreSQL 16 on port `5432` with a persistent named volume `dev_postgres_data`.
- **Backend Spring Boot:** Can run either natively via `./mvnw spring-boot:run` connecting to local PostgreSQL, or inside a container.
- **Frontend Angular:** Runs natively via `npm start` with proxy configuration to `http://localhost:8080`.
- **Photo Storage:** Mounted to a local directory `./docker/volumes/photos`.

#### 1.2 Production Topology
Hardened containerized deployment running behind an Nginx reverse proxy on the host server:

```
                          INTERNET / INTRANET
                                   │
                                   ▼ [Ports 80 & 443]
┌────────────────────────────────────────────────────────────────────────┐
│                      REVERSE PROXY (NGINX CONTAINER)                   │
│  - Enforces TLS 1.3 / HTTPS                                            │
│  - Distributes compiled Angular SPA static bundles                     │
│  - Applies IP-level rate limiting (10 req/s with burst=20)             │
│  - Reverse-proxies /api/* to Spring Boot container                     │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ Internal Docker Network (app_net)
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   BACKEND APP (SPRING BOOT 3 CONTAINER)                │
│  - Executable JAR running on Eclipse Temurin 21 JRE                    │
│  - Runs as unprivileged non-root user (UID 10001)                      │
│  - Memory limited: 2048 MB RAM, 1.5 vCPU                               │
│  - Mounts persistent volume: photo_storage ➔ /var/data/attendance/photos│
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ Internal Docker Network (app_net)
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     DATABASE (POSTGRESQL 16 CONTAINER)                 │
│  - Port 5432 strictly internal (NO HOST PORT MAPPING)                  │
│  - WAL enabled (wal_level = replica)                                   │
│  - Mounts persistent volume: postgres_data ➔ /var/lib/postgresql/data  │
└────────────────────────────────────────────────────────────────────────┘
```

---

### 2. Container Boundaries & Isolation Invariants

1. **Database Port Concealment:** The PostgreSQL port `5432` is strictly attached to the internal Docker bridge network (`app_net`). It is never published to the host (`ports: "5432:5432"` is forbidden in production).
2. **Non-Root Execution:** The Spring Boot application Dockerfile uses a multi-stage build that drops root privileges, running under dedicated system user `appuser`:
   ```dockerfile
   RUN addgroup --system --gid 10001 appgroup && \
       adduser --system --uid 10001 --ingroup appgroup appuser
   USER appuser:appgroup
   ```
3. **Volume Separation:** Database storage (`postgres_data`) and photo storage (`photo_storage`) are defined as separate named Docker volumes, enabling independent backup schedules, retention policies, and storage quotas.
4. **Health Check Probes:** Every container specifies an explicit `healthcheck`:
   - PostgreSQL: `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB`
   - Spring Boot: `curl -f http://localhost:8080/actuator/health/readiness`
   - Nginx: `curl -f http://localhost:80/`
