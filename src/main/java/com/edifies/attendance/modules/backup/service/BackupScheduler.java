package com.edifies.attendance.modules.backup.service;

import com.edifies.attendance.modules.backup.model.BackupManifest;
import com.edifies.attendance.modules.backup.model.BackupType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final DisasterRecoveryCoordinator coordinator;

    @Value("${attendance.backup.enabled:true}")
    private boolean backupEnabled;

    public BackupScheduler(DisasterRecoveryCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(cron = "${attendance.backup.cron:0 0 1 * * ?}")
    public void runScheduledBackup() {
        if (!backupEnabled) {
            log.info("Scheduled backup is disabled by configuration (attendance.backup.enabled=false).");
            return;
        }

        log.info("Starting automated scheduled disaster recovery backup...");
        try {
            BackupManifest manifest = coordinator.triggerBackup(
                    BackupType.SCHEDULED,
                    "Automated daily scheduled backup",
                    false,
                    "SYSTEM_SCHEDULER"
            );
            log.info("Automated scheduled backup completed: id={}, size={} bytes, duration={}ms",
                    manifest.getBackupId(), manifest.getTotalSizeBytes(), manifest.getDurationMs());
        } catch (Exception e) {
            log.error("Automated scheduled disaster recovery backup failed: {}", e.getMessage(), e);
        }
    }
}
