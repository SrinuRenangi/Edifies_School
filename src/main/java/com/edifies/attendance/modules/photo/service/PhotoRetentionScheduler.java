package com.edifies.attendance.modules.photo.service;

import com.edifies.attendance.modules.photo.dto.CleanupExecutionResponse;
import com.edifies.attendance.modules.photo.model.CleanupRunType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PhotoRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PhotoRetentionScheduler.class);

    private final PhotoRetentionService photoRetentionService;

    public PhotoRetentionScheduler(PhotoRetentionService photoRetentionService) {
        this.photoRetentionService = photoRetentionService;
    }

    /**
     * Executes recurring automated cleanup according to the configured retention policy.
     * Default cron: 2:00 AM daily.
     * Automatically skips execution if administrator policy has retention disabled.
     */
    @Scheduled(cron = "${attendance.photo.retention-cron:0 0 2 * * *}")
    public void runScheduledRetentionCleanup() {
        log.info("Triggered scheduled photo retention cleanup task.");
        try {
            CleanupExecutionResponse response = photoRetentionService.executeCleanup("SYSTEM_SCHEDULER", CleanupRunType.SCHEDULED);
            if (response.photosEvaluated() > 0) {
                log.info("Scheduled photo retention cleanup finished: deleted={}, bytesReclaimed={}, status={}",
                        response.photosDeleted(), response.bytesReclaimed(), response.status());
            } else {
                log.debug("Scheduled photo retention cleanup finished with zero eligible items.");
            }
        } catch (Exception ex) {
            log.error("Scheduled photo retention cleanup failed with error: {}", ex.getMessage(), ex);
        }
    }
}
