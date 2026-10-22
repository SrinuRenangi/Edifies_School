export interface DailyAttendanceSummary {
  workDate: string;
  attendanceId?: string;
  status?: 'CHECKED_IN' | 'CHECKED_OUT' | 'AUTO_CLOSED';
  checkInTime?: string;
  checkOutTime?: string;
  durationMinutes?: number;
  durationFormatted?: string;
  hasCheckInPhoto: boolean;
  hasCheckOutPhoto: boolean;
  hasRecord: boolean;
  note: string;
}

export interface EmployeeMonthlySummary {
  employeeId: string;
  employeeCode: string;
  employeeName: string;
  department: string;
  employeeStatus: string;
  totalSessions: number;
  completedSessions: number;
  incompleteSessions: number;
  totalDurationMinutes: number;
  totalDurationFormatted: string;
  dailyRecords: DailyAttendanceSummary[];
}

export interface MonthlyReportResponse {
  month: number;
  year: number;
  totalDaysInMonth: number;
  totalEmployees: number;
  totalRecordedSessions: number;
  totalCompletedSessions: number;
  totalIncompleteSessions: number;
  overallDurationMinutes: number;
  overallDurationFormatted: string;
  employeeSummaries: EmployeeMonthlySummary[];
}
