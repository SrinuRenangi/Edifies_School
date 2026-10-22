package com.edifies.attendance.modules.photo.model;

/**
 * Lifecycle states for attendance photos.
 * Represents the full progression from initial upload to secure retention purging.
 */
public enum StorageStatus {
    ACTIVE,             // File is available on storage
    PENDING_DELETION,   // Claimed by a cleanup worker; deletion in progress
    PURGED,             // Binary file safely deleted under retention policy; metadata preserved
    MISSING,            // Metadata exists but referenced file was absent prior to cleanup
    CLEANUP_FAILED,     // Attempted deletion failed due to storage/filesystem error; retryable
    ARCHIVED            // Relocated to secondary/cold archive storage
}
