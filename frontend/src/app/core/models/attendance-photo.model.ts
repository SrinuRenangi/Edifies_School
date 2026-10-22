export type PhotoType = 'CHECK_IN' | 'CHECK_OUT';
export type StorageStatus = 'ACTIVE' | 'PENDING_DELETION' | 'PURGED' | 'MISSING' | 'CLEANUP_FAILED' | 'ARCHIVED';

export interface AttendancePhoto {
  id: string;
  attendanceId: string;
  employeeId: string;
  photoType: PhotoType;
  storagePath: string;
  fileName: string;
  mimeType: string;
  fileSize: number;
  imageWidth: number;
  imageHeight: number;
  storageStatus: StorageStatus;
  purgedAt?: string | null;
  cleanupError?: string | null;
  cleanupAttempts?: number;
  retentionDeadline?: string | null;
  createdAt: string;
}

export interface CameraState {
  isStreaming: boolean;
  hasPermission: boolean | null;
  errorMessage: string | null;
  capturedBlob: Blob | null;
  previewUrl: string | null;
}

