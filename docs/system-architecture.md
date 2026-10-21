# System Architecture Specification
## Staff Attendance Management System — Phase 3 Technical Blueprint

**Document Version:** 1.0.0  
**Status:** Approved Technical Architecture  
**Architectural Style:** Clean Modular Monolith  
**Runtime:** Java 21 LTS (OpenJDK Temurin) / Spring Boot 3.3+  
**Database:** PostgreSQL 16  
**Frontend:** Angular 18+ Standalone SPA  

---

### 1. Architectural Style & Monolith Justification
The system is architected as a **Modular Monolith** packaged into a single containerized runtime, fronted by an Nginx reverse proxy.

#### Why Modular Monolith for a 20-50 Person Enterprise?
1. **Zero Distributed Systems Tax:** Eliminates network serialization latency, distributed saga transactions, dual-write failures, and RPC network partitions.
2. **ACID Transaction Guarantees:** Attendance invariants (e.g., single open session per employee, idempotency verification, audit logging) are enforced atomically within local PostgreSQL transactions.
3. **Operational Simplicity:** A single deployable JAR running on an existing server requires minimal RAM (~1.5GB to 2.0GB JVM heap) and zero Kubernetes/Kafka/Redis operational overhead.
4. **Clean Domain Boundaries:** Enforces strict package encapsulation and interfaces (`package-by-feature`), ensuring that if the company scales to tens of thousands of staff in the future, modules can be cleanly extracted into microservices without rewriting domain logic.

---

### 2. Layer Responsibilities & Architectural Boundaries

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION LAYER (ANGULAR 18+)                │
│  - Renders Kiosk Touch UI & Admin Dashboard                            │
│  - Manages Browser WebCam Streams & Canvas JPEG Encoding               │
│  - Never decides attendance state; displays server-authoritative state  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTPS / REST (JSON & Multipart)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        API / CONTROLLER LAYER                          │
│  - Request routing, payload deserialization, Bean Validation (@Valid)  │
│  - JWT Cookie & Kiosk Device Token extraction                          │
│  - Maps application exceptions to RFC-7807 Problem Details             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Invokes Application Services
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     APPLICATION / SERVICE LAYER                        │
│  - Orchestrates business use cases (Punch, Registration, Adjustments)  │
│  - Defines Transaction Boundaries (@Transactional)                     │
│  - Coordinates between domain entities, photo storage SPI, and audit   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Operates on Domain Model
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     DOMAIN / BUSINESS-RULE LAYER                       │
│  - Enforces State Machine transitions (NOT_CHECKED_IN ➔ CHECKED_IN)    │
│  - Calculates Work-Date boundary according to Asia/Kolkata cutoff      │
│  - Pure business invariants independent of HTTP or database technology │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Relational Mappings & Repositories
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   PERSISTENCE LAYER (SPRING DATA JPA)                  │
│  - Manages relational entities, queries, and optimistic locking tokens │
│  - Enforces relational constraints (FOREIGN KEY ON DELETE RESTRICT)    │
│  - Executes Flyway version-controlled migrations                       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Storage SPI
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    STORAGE INTEGRATION LAYER (SPI)                     │
│  - Validates image magic bytes & strips EXIF metadata                  │
│  - Persists JPEG files to private non-web filesystem directory         │
│  - Provides pluggable PhotoStorageService interface for future S3/MinIO│
└────────────────────────────────────────────────────────────────────────┘
```

---

### 3. Cross-Cutting Concerns

1. **Authentication & Authorization:** Enforced via Spring Security 6 filter chains. Separate filter chains isolate the stateful/tokenized Admin dashboard (JWT in HttpOnly cookie) from the kiosk endpoint (`X-Kiosk-Device-Token` + PIN).
2. **Audit Logging:** Implemented using Hibernate interceptors and Spring Application Events. All state mutations trigger synchronous audit log persistence within the same database transaction.
3. **Structured Logging:** Centralized JSON logging via Logback, including Correlation ID (`X-Correlation-Id`), authenticated actor, endpoint, and latency. Sensitive fields (PIN, passwords, raw image data) are strictly masked.
4. **Health & Observability:** Spring Boot Actuator exposing `/actuator/health` (liveness/readiness probes) checking PostgreSQL connectivity, disk space headroom, and storage volume availability.
