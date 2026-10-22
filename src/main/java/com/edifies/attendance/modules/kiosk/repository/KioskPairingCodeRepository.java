package com.edifies.attendance.modules.kiosk.repository;

import com.edifies.attendance.modules.kiosk.model.KioskPairingCode;
import com.edifies.attendance.modules.kiosk.model.PairingCodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KioskPairingCodeRepository extends JpaRepository<KioskPairingCode, UUID> {

    Optional<KioskPairingCode> findByPairingCodeHash(String pairingCodeHash);

    List<KioskPairingCode> findAllByOrderByCreatedAtDesc();

    List<KioskPairingCode> findByStatusAndExpiresAtBefore(PairingCodeStatus status, Instant now);

    List<KioskPairingCode> findByStatusAndExpiresAtAfter(PairingCodeStatus status, Instant now);
}
