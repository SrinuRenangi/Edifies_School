package com.edifies.attendance.modules.kiosk.repository;

import com.edifies.attendance.modules.kiosk.model.KioskDeviceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KioskDeviceAuditLogRepository extends JpaRepository<KioskDeviceAuditLog, UUID> {

    List<KioskDeviceAuditLog> findTop100ByOrderByCreatedAtDesc();

    List<KioskDeviceAuditLog> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId);
}
