package com.edifies.attendance.modules.photo.controller;

import com.edifies.attendance.modules.photo.dto.*;
import com.edifies.attendance.modules.photo.model.CleanupRunType;
import com.edifies.attendance.modules.photo.service.PhotoRetentionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/storage")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStorageController {

    private final PhotoRetentionService photoRetentionService;

    public AdminStorageController(PhotoRetentionService photoRetentionService) {
        this.photoRetentionService = photoRetentionService;
    }

    @GetMapping("/health")
    public ResponseEntity<StorageHealthResponse> getStorageHealth() {
        StorageHealthResponse health = photoRetentionService.getStorageHealth();
        return ResponseEntity.ok(health);
    }

    @GetMapping("/retention/policy")
    public ResponseEntity<PhotoRetentionPolicyResponse> getRetentionPolicy() {
        return ResponseEntity.ok(PhotoRetentionPolicyResponse.from(photoRetentionService.getPolicy()));
    }

    @PutMapping("/retention/policy")
    public ResponseEntity<PhotoRetentionPolicyResponse> updateRetentionPolicy(
            @Valid @RequestBody PhotoRetentionPolicyRequest request,
            Authentication authentication) {
        String performedBy = authentication != null ? authentication.getName() : "ADMIN";
        PhotoRetentionPolicyResponse response = photoRetentionService.updatePolicy(request, performedBy);
        return ResponseEntity.ok(response);
    }

    @RequestMapping(value = "/retention/dry-run", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<RetentionDryRunResponse> performDryRun(Authentication authentication) {
        String performedBy = authentication != null ? authentication.getName() : "ADMIN";
        RetentionDryRunResponse response = photoRetentionService.performDryRun(performedBy);
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/retention/cleanup", "/cleanup/execute"})
    public ResponseEntity<CleanupExecutionResponse> executeCleanup(Authentication authentication) {
        String performedBy = authentication != null ? authentication.getName() : "ADMIN";
        CleanupExecutionResponse response = photoRetentionService.executeCleanup(performedBy, CleanupRunType.MANUAL);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/retention/history", "/cleanup/history"})
    public ResponseEntity<Page<CleanupExecutionResponse>> getCleanupHistory(
            @PageableDefault(size = 10) Pageable pageable) {
        Page<CleanupExecutionResponse> history = photoRetentionService.getCleanupHistory(pageable);
        return ResponseEntity.ok(history);
    }

    @RequestMapping(value = "/orphans/scan", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<StorageOrphanScanResponse> scanOrphans() {
        StorageOrphanScanResponse response = photoRetentionService.scanOrphans();
        return ResponseEntity.ok(response);
    }
}
