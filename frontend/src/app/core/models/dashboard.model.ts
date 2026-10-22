export interface DashboardActivityItem {
  recordId: string | null;
  employeeId: string | null;
  employeeCode: string;
  employeeName: string;
  department: string;
  activityType: 'CHECK_IN' | 'CHECK_OUT' | 'CORRECTION' | 'AUTO_CLOSED';
  timestamp: string;
  description: string;
  status: string;
  hasPhoto: boolean;
}

export interface DashboardAttentionItem {
  recordId: string;
  employeeId: string;
  employeeCode: string;
  employeeName: string;
  department: string;
  workDate: string;
  checkInTime: string;
  status: string;
  issueType: 'STALE_OPEN_SESSION' | 'AUTO_CLOSED' | 'OVERDUE_OPEN_SESSION';
  issueDescription: string;
  recommendation: string;
}

export interface DashboardSummary {
  workDate: string;
  timezone: string;
  generatedAt: string;
  activeEmployeeCount: number;
  totalEmployeeCount: number;
  currentlyCheckedInCount: number;
  completedSessionCount: number;
  incompleteSessionCount: number;
  notCheckedInCount: number;
  recentActivities: DashboardActivityItem[];
  attentionRequiredSessions: DashboardAttentionItem[];
}

export interface DashboardQuickSearchEmployee {
  id: string;
  employeeId: string;
  fullName: string;
  department: string;
  status: 'ACTIVE' | 'INACTIVE';
  todayStatus: 'CHECKED_IN' | 'CHECKED_OUT' | 'AUTO_CLOSED' | 'NO_RECORD';
  lastCheckInTime: string | null;
  lastAttendanceId: string | null;
}
