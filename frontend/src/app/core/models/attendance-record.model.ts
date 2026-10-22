export interface AttendanceRecordResponse {
  id: string;
  employeeId: string;
  employeeCode: string;
  employeeName: string;
  department: string;
  workDate: string;
  status: 'CHECKED_IN' | 'CHECKED_OUT' | 'AUTO_CLOSED' | 'VOIDED';
  checkInTime: string;
  checkOutTime?: string;
  hasCheckInPhoto: boolean;
  hasCheckOutPhoto: boolean;
  durationMinutes?: number;
  durationFormatted?: string;
  isIncomplete: boolean;
  correctionIndicator?: string;
  version?: number;
  message?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
