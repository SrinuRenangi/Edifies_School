package com.edifies.attendance.modules.backup.controller;

import com.edifies.attendance.modules.backup.dto.*;
import com.edifies.attendance.modules.backup.model.BackupManifest;
import com.edifies.attendance.modules.backup.model.BackupRetentionPolicy;
import com.edifies.attendance.modules.backup.model.BackupType;
import com.edifies.attendance.modules.backup.service.BackupRetentionService;
import com.edifies.attendance.modules.backup.service.DisasterRecoveryCoordinator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/backup")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBackupController {

    private final DisasterRecoveryCoordinator coordinator;
    private final BackupRetentionService retentionService;

    public AdminBackupController(DisasterRecoveryCoordinator coordinator, BackupRetentionService retentionService) {
        this.coordinator = coordinator;
        this.retentionService = retentionService;
    }

    @PostMapping("/trigger")
    public ResponseEntity<BackupSummaryResponse> triggerBackup(
            @RequestBody(required = false) BackupTriggerRequest request,
            Principal principal
    ) throws Exception {
        String actor = principal != null ? principal.getName() : "ADMIN";
        BackupType type = (request != null && request.type() != null)
                ? BackupType.valueOf(request.type().toUpperCase())
                : BackupType.MANUAL;
        String notes = request != null ? request.notes() : "Manual backup triggered by admin";
        Boolean encrypt = request != null ? request.encrypt() : null;

        BackupManifest manifest = coordinator.triggerBackup(type, notes, encrypt, actor);
        return ResponseEntity.ok(BackupSummaryResponse.from(manifest));
    }

    @GetMapping("/status")
    public ResponseEntity<BackupHealthResponse> getStatus() {
        return ResponseEntity.ok(coordinator.getHealthStatus());
    }

    @GetMapping("/history")
    public ResponseEntity<List<BackupSummaryResponse>> listBackups() {
        return ResponseEntity.ok(coordinator.listBackups());
    }

    @GetMapping("/download/{backupId}")
    public ResponseEntity<Resource> downloadBackup(@PathVariable String backupId) {
        Path archiveFile = coordinator.getBackupArchiveFile(backupId);
        Resource resource = new FileSystemResource(archiveFile);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archiveFile.getFileName().toString() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/restore-test")
    public ResponseEntity<RestoreValidationResponse> runRestoreTest(
            @RequestBody(required = false) RestoreValidationRequest request
    ) throws Exception {
        String backupId = request != null ? request.backupId() : null;
        RestoreValidationResponse response = coordinator.runRestoreVerification(backupId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/retention")
    public ResponseEntity<BackupRetentionPolicy> getRetentionPolicy() {
        return ResponseEntity.ok(retentionService.getPolicy());
    }

    @PutMapping("/retention")
    public ResponseEntity<BackupRetentionPolicy> updateRetentionPolicy(@RequestBody BackupRetentionPolicy policy) {
        retentionService.updatePolicy(policy);
        return ResponseEntity.ok(retentionService.getPolicy());
    }
}
