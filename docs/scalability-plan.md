# Scalability Plan & Capacity Thresholds
## Staff Attendance Management System — Phase 3 Technical Specification

**Document Version:** 1.0.0  
**Status:** Approved Scalability Plan  

---

### 1. Three-Year Multi-Tier Growth Projections

Projections assume 300 operational days/year and 2 attendance photos (Check-In + Check-Out) per completed session.

| Growth Tier | Active Employees | Annual Sessions | 3-Year Total Sessions | 3-Year DB Storage (Rows + Indexes + Audit) | 3-Year Photo Storage (At 200 KB/Photo) | With 90-Day Rolling Purge |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Tier 1: Initial Baseline** | **20** | 6,000 | 18,000 | **~100 MB** | 7.2 GB | **0.6 GB** |
| **Tier 2: Mid-Size Growth** | **100** | 30,000 | 90,000 | **~500 MB** | 36.0 GB | **3.0 GB** |
| **Tier 3: Enterprise Expansion** | **500** | 150,000 | 450,000 | **~2.5 GB** | 180.0 GB | **15.0 GB** |

*Conclusion:* Even at 500 employees (a 25x increase), the complete 3-year database footprint is only ~2.5 GB. PostgreSQL handles multi-gigabyte databases entirely in memory cache on modest modern hardware.

---

### 2. Built-In Architectural Scaling Features (Release 1)
1. **Directory Inode Partitioning:** Photos are partitioned into year/month directories (`/photos/YYYY/MM/<uuid>.jpg`). This prevents directory inode saturation and filesystem slowdowns when thousands of photos accumulate.
2. **Composite Partial Indexes:** Query plans for `(employee_id, work_date DESC)` use index-only scans, ensuring $O(\log N)$ lookup performance whether the table has 10,000 or 10,000,000 rows.
3. **Stateless Spring Boot Service:** The application server stores zero user session state in local memory. Authentication is governed by cryptographic tokens. If multiple kiosk terminals are deployed across multiple building entrances, all traffic targets the same stateless backend seamlessly.
4. **Streaming CSV Exporters:** CSV generation uses JPA streaming cursors (`Stream<AttendanceSession>`) with Jackson CSV writers, keeping JVM heap utilization constant (< 50MB) regardless of date range size.

---

### 3. Operational Thresholds for Infrastructure Scaling

```
┌────────────────────────────────────────────────────────────────────────┐
│                      INFRASTRUCTURE UPGRADE TRIGGERS                   │
├─────────────────────┬──────────────────────────┬───────────────────────┤
│ Operational Signal  │ Threshold Metric         │ Architectural Action  │
├─────────────────────┼──────────────────────────┼───────────────────────┤
│ CPU Saturation      │ Sustained > 75% for 15m  │ Add 2nd app instance  │
│                     │                          │ behind Nginx upstream │
├─────────────────────┼──────────────────────────┼───────────────────────┤
│ Local Disk Capacity │ Volume Usage > 75%       │ Switch photo SPI from │
│                     │                          │ LocalDisk to MinIO/S3 │
├─────────────────────┼──────────────────────────┼───────────────────────┤
│ DB Query Saturation │ P99 Query Latency > 1.5s │ Tune buffer pool /    │
│                     │                          │ add read-only replica │
├─────────────────────┼──────────────────────────┼───────────────────────┤
│ Concurrent Kiosks   │ > 10 Physical Terminals  │ Connection pool boost │
│                     │                          │ (HikariCP max-pool 50)│
└─────────────────────┴──────────────────────────┴───────────────────────┘
```

**Anti-Pattern Rejection:** Under no circumstances will Redis, Kafka, or Kubernetes be introduced for the initial ~20 employee deployment. The modular monolith handles this workload using less than 15% of a basic single-server profile.
