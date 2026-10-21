# ADR-004: Pluggable Photo Storage SPI & Local Filesystem Provider
**Status:** APPROVED  
**Date:** 2026-10-21  

### Context and Problem Statement
Attendance punches require photographic proof of physical presence. Storing multi-megabyte binary images inside PostgreSQL degrades WAL flushes and table scans. Using proprietary cloud object storage (AWS S3) introduces external billing and internet egress dependencies.

### Selected Decision
Define a pluggable `PhotoStorageService` interface (SPI) backed initially by a hardened **Local Filesystem Provider** (`LocalFileSystemPhotoStorage`) storing photos outside the web root on a dedicated Docker volume.

### Technical Invariants
- Storage directory: `/var/data/attendance/photos/YYYY/MM/<random-uuid>.jpg`.
- The storage implementation is completely decoupled from business domain logic.
- If the company migrates to AWS S3, MinIO, or Google Cloud Storage in the future, only a new implementation of `PhotoStorageService` needs to be provided without modifying attendance logic.
- Photos are never publicly accessible via direct web server URLs; retrieval requires authenticated Spring Boot streaming.
