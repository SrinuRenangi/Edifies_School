export type CorrectionActionType =
  | 'CORRECT_CHECK_IN'
  | 'CORRECT_CHECK_OUT'
  | 'CORRECT_BOTH'
  | 'RESOLVE_MISSED_CHECKOUT'
  | 'VOID_RECORD'
  | 'MANUAL_ENTRY';

export interface AttendanceCorrectionRequest {
  checkInTime?: string;
  checkOutTime?: string;
  status?: 'CHECKED_IN' | 'CHECKED_OUT' | 'AUTO_CLOSED' | 'VOIDED';
  actionType?: CorrectionActionType;
  reason: string;
  recordVersion?: number;
}

export interface ManualAttendanceRequest {
  employeeId: string;
  workDate: string;
  checkInTime: string;
  checkOutTime?: string;
  reason: string;
}

export interface AttendanceCorrectionResponse {
  id: string;
  attendanceRecordId: string;
  employeeId: string;
  employeeCode: string;
  employeeName: string;
  actionType: CorrectionActionType;
  previousCheckInTime?: string;
  correctedCheckInTime?: string;
  previousCheckOutTime?: string;
  correctedCheckOutTime?: string;
  previousStatus?: string;
  correctedStatus: string;
  reason: string;
  performedBy: string;
  createdAt: string;
  recordVersion: number;
}
