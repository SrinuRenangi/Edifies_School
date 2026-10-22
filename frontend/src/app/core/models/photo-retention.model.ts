export type StorageWarningLevel = 'NORMAL' | 'WARNING' | 'CRITICAL';
export type CleanupRunType = 'SCHEDULED' | 'MANUAL' | 'DRY_RUN';
export type CleanupRunStatus = 'SUCCESS' | 'PARTIAL' | 'FAILED';

export interface PhotoRetentionPolicy {
  enabled: boolean;
  retentionDays: number;
  batchSize: number;
  updatedAt?: string;
  updatedBy?: string;
  notes?: string;
}

export interface PhotoRetentionPolicyRequest {
  enabled: boolean;
  retentionDays: number;
  batchSize: number;
  notes?: string;
}

export interface StorageHealth {
  accessible: boolean;
  writable: boolean;
  deletable: boolean;
  totalSpaceBytes: number;
  freeSpaceBytes: number;
  usableSpaceBytes: number;
  warningLevel: StorageWarningLevel;
  totalPhotosCount: number;
  activePhotosCount: number;
  purgedPhotosCount: number;
  missingPhotosCount: number;
  failedCleanupCount: number;
  totalPhotoSizeBytes: number;
  tempFilesCount: number;
  tempFilesSizeBytes: number;
  lastSuccessfulCleanup?: string;
  measuredAt: string;
}

export interface RetentionDryRun {
  totalPhotosExamined: number;
  eligibleForDeletionCount: number;
  estimatedBytesEligible: number;
  excludedCount: number;
  requiringInvestigationCount: number;
  retentionPolicyEnabled: boolean;
  retentionDays: number;
  cutoffTime: string;
  details: string;
}

export interface CleanupExecutionResult {
  runId: string;
  runType: CleanupRunType;
  status: CleanupRunStatus;
  itemsExamined: number;
  photosPurged: number;
  bytesReclaimed: number;
  tempFilesPurged: number;
  inconsistenciesDetected: number;
  startedAt: string;
  completedAt: string;
  summary: string;
  errors?: string[];
}

export interface StorageOrphanScan {
  unregisteredFilesCount: number;
  unregisteredFiles: string[];
  abandonedTempFilesCount: number;
  abandonedTempFilesCleaned: number;
  missingDbPhotosCount: number;
  missingDbPhotoIds: string[];
  scanDurationMs: number;
  notes: string;
}
