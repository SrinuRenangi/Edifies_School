package com.edifies.attendance.modules.backup.service;

import com.edifies.attendance.modules.backup.model.BackupManifest;
import com.edifies.attendance.modules.backup.model.BackupRetentionPolicy;
import com.edifies.attendance.modules.backup.model.BackupStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class BackupRetentionService {

    private static final Logger log = LoggerFactory.getLogger(BackupRetentionService.class);

    @Value("${attendance.backup.retention.keep-daily:7}")
    private int defaultKeepDaily = 7;

    @Value("${attendance.backup.retention.keep-weekly:4}")
    private int defaultKeepWeekly = 4;

    @Value("${attendance.backup.retention.keep-monthly:3}")
    private int defaultKeepMonthly = 3;

    private BackupRetentionPolicy currentPolicy;

    public synchronized BackupRetentionPolicy getPolicy() {
        if (currentPolicy == null) {
            currentPolicy = new BackupRetentionPolicy(defaultKeepDaily, defaultKeepWeekly, defaultKeepMonthly);
        }
        return currentPolicy;
    }

    public synchronized void updatePolicy(BackupRetentionPolicy policy) {
        if (policy != null) {
            this.currentPolicy = policy;
            log.info("Updated backup retention policy: keepDaily={}, keepWeekly={}, keepMonthly={}",
                    policy.getKeepDaily(), policy.getKeepWeekly(), policy.getKeepMonthly());
        }
    }

    public record RetentionResult(
            int evaluatedCount,
            int preservedCount,
            int prunedCount,
            List<String> prunedBackupIds,
            List<String> auditLogs
    ) {}

    public RetentionResult applyRetention(Path storageRoot, List<BackupManifest> manifests) {
        return applyRetentionPolicy(storageRoot, manifests, getPolicy());
    }

    public RetentionResult applyRetentionPolicy(Path storageRoot, List<BackupManifest> manifests, BackupRetentionPolicy policy) {
        if (manifests == null || manifests.isEmpty()) {
            return new RetentionResult(0, 0, 0, Collections.emptyList(), Collections.emptyList());
        }

        List<BackupManifest> sorted = new ArrayList<>(manifests);
        // Sort descending (newest first)
        sorted.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        Set<String> toPreserve = new HashSet<>();
        List<String> auditLogs = new ArrayList<>();
        List<String> pruned = new ArrayList<>();

        // 1. In-progress backups must never be deleted
        for (BackupManifest m : sorted) {
            if (m.getStatus() == BackupStatus.IN_PROGRESS) {
                toPreserve.add(m.getBackupId());
                auditLogs.add("Preserving in-progress backup: " + m.getBackupId());
            }
        }

        // 2. Rule: Always preserve the newest verified backup under all conditions
        BackupManifest newestVerified = sorted.stream()
                .filter(m -> m.getStatus() == BackupStatus.VERIFIED || m.getStatus() == BackupStatus.COMPLETED)
                .findFirst()
                .orElse(null);

        if (newestVerified != null) {
            toPreserve.add(newestVerified.getBackupId());
            auditLogs.add("Preserving latest known-good backup: " + newestVerified.getBackupId());
        } else if (!sorted.isEmpty()) {
            // If no backup is verified yet, preserve at least the newest backup
            toPreserve.add(sorted.get(0).getBackupId());
            auditLogs.add("Preserving newest existing backup: " + sorted.get(0).getBackupId());
        }

        // 3. Daily retention: Keep 1 backup per day for the last N days
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        Map<LocalDate, BackupManifest> dailyBackups = new HashMap<>();
        for (BackupManifest m : sorted) {
            if (m.getStatus() != BackupStatus.VERIFIED && m.getStatus() != BackupStatus.COMPLETED) {
                continue;
            }
            LocalDate backupDate = m.getCreatedAt().atZone(ZoneId.of("UTC")).toLocalDate();
            long daysAgo = ChronoUnit.DAYS.between(backupDate, today);
            if (daysAgo >= 0 && daysAgo < policy.getKeepDaily()) {
                if (!dailyBackups.containsKey(backupDate)) {
                    dailyBackups.put(backupDate, m);
                    toPreserve.add(m.getBackupId());
                    auditLogs.add("Preserving daily backup for date " + backupDate + ": " + m.getBackupId());
                }
            }
        }

        // 4. Weekly checkpoints: Keep 1 backup per week for the last N weeks
        Map<String, BackupManifest> weeklyBackups = new HashMap<>();
        for (BackupManifest m : sorted) {
            if (m.getStatus() != BackupStatus.VERIFIED && m.getStatus() != BackupStatus.COMPLETED) {
                continue;
            }
            LocalDate backupDate = m.getCreatedAt().atZone(ZoneId.of("UTC")).toLocalDate();
            long daysAgo = ChronoUnit.DAYS.between(backupDate, today);
            long weeksAgo = daysAgo / 7;
            if (weeksAgo > 0 && weeksAgo <= policy.getKeepWeekly()) {
                String weekKey = backupDate.getYear() + "-W" + (backupDate.getDayOfYear() / 7);
                if (!weeklyBackups.containsKey(weekKey)) {
                    weeklyBackups.put(weekKey, m);
                    toPreserve.add(m.getBackupId());
                    auditLogs.add("Preserving weekly checkpoint for " + weekKey + ": " + m.getBackupId());
                }
            }
        }

        // 5. Monthly checkpoints: Keep 1 backup per month for the last N months
        Map<String, BackupManifest> monthlyBackups = new HashMap<>();
        for (BackupManifest m : sorted) {
            if (m.getStatus() != BackupStatus.VERIFIED && m.getStatus() != BackupStatus.COMPLETED) {
                continue;
            }
            LocalDate backupDate = m.getCreatedAt().atZone(ZoneId.of("UTC")).toLocalDate();
            String monthKey = backupDate.getYear() + "-" + String.format("%02d", backupDate.getMonthValue());
            if (!monthlyBackups.containsKey(monthKey) && monthlyBackups.size() < policy.getKeepMonthly()) {
                monthlyBackups.put(monthKey, m);
                toPreserve.add(m.getBackupId());
                auditLogs.add("Preserving monthly checkpoint for " + monthKey + ": " + m.getBackupId());
            }
        }

        // 6. Delete candidates that are not in the preserve set
        for (BackupManifest m : sorted) {
            if (!toPreserve.contains(m.getBackupId())) {
                boolean deleted = deleteBackupFiles(storageRoot, m);
                if (deleted) {
                    pruned.add(m.getBackupId());
                    auditLogs.add("Pruned expired backup: " + m.getBackupId());
                    log.info("Retention pruned expired backup: {}", m.getBackupId());
                }
            }
        }

        return new RetentionResult(sorted.size(), toPreserve.size(), pruned.size(), pruned, auditLogs);
    }

    private boolean deleteBackupFiles(Path storageRoot, BackupManifest manifest) {
        try {
            Path backupDir = storageRoot.resolve(manifest.getBackupId());
            if (Files.exists(backupDir)) {
                // Delete directory contents
                Files.walk(backupDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
            if (manifest.getArchiveFileName() != null) {
                Path archiveFile = storageRoot.resolve(manifest.getArchiveFileName());
                Files.deleteIfExists(archiveFile);
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to delete expired backup files for {}: {}", manifest.getBackupId(), e.getMessage());
            return false;
        }
    }
}
