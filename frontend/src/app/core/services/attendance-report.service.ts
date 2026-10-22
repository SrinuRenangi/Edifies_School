import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MonthlyReportResponse } from '../models/monthly-report.model';
import { AttendanceRecordResponse, PageResponse } from '../models/attendance-record.model';

@Injectable({
  providedIn: 'root'
})
export class AttendanceReportService {
  private readonly reportsApiUrl = '/api/admin/reports';
  private readonly attendanceApiUrl = '/api/admin/attendance';

  constructor(private http: HttpClient) {}

  /**
   * Fetches paginated attendance records with search, date range, status, and employee filters.
   */
  getAttendanceRecords(
    search?: string,
    employeeId?: string,
    fromDate?: string,
    toDate?: string,
    status?: string,
    page: number = 0,
    size: number = 20
  ): Observable<PageResponse<AttendanceRecordResponse>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (search) params = params.set('search', search);
    if (employeeId) params = params.set('employeeId', employeeId);
    if (fromDate) params = params.set('fromDate', fromDate);
    if (toDate) params = params.set('toDate', toDate);
    if (status) params = params.set('status', status);

    return this.http.get<PageResponse<AttendanceRecordResponse>>(this.attendanceApiUrl, {
      params,
      withCredentials: true
    });
  }

  /**
   * Fetches aggregated monthly report for all staff or a single employee.
   */
  getMonthlyReport(
    month: number,
    year: number,
    employeeId?: string,
    search?: string
  ): Observable<MonthlyReportResponse> {
    let params = new HttpParams()
      .set('month', month.toString())
      .set('year', year.toString());

    if (employeeId) params = params.set('employeeId', employeeId);
    if (search) params = params.set('search', search);

    return this.http.get<MonthlyReportResponse>(`${this.reportsApiUrl}/monthly`, {
      params,
      withCredentials: true
    });
  }

  /**
   * Downloads filtered attendance records CSV export.
   */
  exportAttendanceCsv(
    search?: string,
    employeeId?: string,
    fromDate?: string,
    toDate?: string,
    status?: string
  ): Observable<Blob> {
    let params = new HttpParams();
    if (search) params = params.set('search', search);
    if (employeeId) params = params.set('employeeId', employeeId);
    if (fromDate) params = params.set('fromDate', fromDate);
    if (toDate) params = params.set('toDate', toDate);
    if (status) params = params.set('status', status);

    return this.http.get(`${this.reportsApiUrl}/attendance/export`, {
      params,
      responseType: 'blob',
      withCredentials: true
    });
  }

  /**
   * Downloads monthly employee summary CSV export.
   */
  exportMonthlySummaryCsv(
    month: number,
    year: number,
    employeeId?: string,
    search?: string
  ): Observable<Blob> {
    let params = new HttpParams()
      .set('month', month.toString())
      .set('year', year.toString());

    if (employeeId) params = params.set('employeeId', employeeId);
    if (search) params = params.set('search', search);

    return this.http.get(`${this.reportsApiUrl}/monthly/export`, {
      params,
      responseType: 'blob',
      withCredentials: true
    });
  }
}
