package com.edifies.attendance.modules.kiosk.repository;

import com.edifies.attendance.modules.kiosk.model.KioskDevice;
import com.edifies.attendance.modules.kiosk.model.KioskDeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KioskDeviceRepository extends JpaRepository<KioskDevice, UUID> {

    Optional<KioskDevice> findByDeviceTokenHash(String deviceTokenHash);

    Optional<KioskDevice> findByDeviceIdentifier(String deviceIdentifier);

    List<KioskDevice> findAllByOrderByCreatedAtDesc();

    List<KioskDevice> findByStatusOrderByCreatedAtDesc(KioskDeviceStatus status);
}
