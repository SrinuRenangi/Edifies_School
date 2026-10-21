# ADR-001: Modular Monolith vs. Microservices Architecture
**Status:** APPROVED  
**Date:** 2026-10-21  
**Deciders:** Principal Architect, Engineering Team  

### Context and Problem Statement
The application manages staff attendance for approximately 20 employees initially (scaling up to 50-100 over 3 years) on a single self-hosted server with zero operational licensing cost. The system must enforce strict transactional consistency across attendance punches, photos, and audit logs.

### Options Considered
1. **Microservices Architecture:** Independent services for Auth, Employee, Attendance, Photo, Reporting, and Audit communicating via HTTP/gRPC and message brokers (Kafka/RabbitMQ).
2. **Unstructured Monolith:** Single codebase without defined package boundaries or interface restrictions.
3. **Modular Monolith:** Single deployable artifact with strictly enforced internal module boundaries (`package-by-feature`), public module APIs, and package-private internals.

### Selected Decision
Option 3: **Modular Monolith** using Spring Boot 3.3+ and Java 21 LTS.

### Reasons for Selection
- Eliminates distributed network latency, distributed tracing overhead, container orchestration complexity (Kubernetes), and dual-write consistency problems.
- Allows local ACID database transactions within PostgreSQL for 100% attendance correctness.
- Keeps memory and CPU footprints minimal (~1.5 GB RAM total system footprint).
- Preserves future modularity: strict boundaries allow extracting high-traffic modules if enterprise scale ever demands it.

### Consequences and Trade-offs
- Deployment requires restarting the single application container.
- High discipline required to prevent developer leakage across package boundaries (mitigated via ArchUnit tests in CI).
