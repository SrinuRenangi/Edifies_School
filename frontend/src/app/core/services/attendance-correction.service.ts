import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AttendanceRecordResponse, PageResponse } from '../models/attendance-record.model';
import { AttendanceCorrectionRequest, AttendanceCorrectionResponse, ManualAttendanceRequest } from '../models/attendance-correction.model';

@Injectable({
  providedIn: 'root'
})
export class AttendanceCorrectionService {
  private readonly baseUrl = '/api/admin/attendance';

  constructor(private http: HttpClient) {}

  getAttendanceRecords(search?: string, employeeId?: string, fromDate?: string, toDate?: string, status?: string, page = 0, size = 20): Observable<PageResponse<AttendanceRecordResponse>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (search) params = params.set('search', search);
    if (employeeId) params = params.set('employeeId', employeeId);
    if (fromDate) params = params.set('fromDate', fromDate);
    if (toDate) params = params.set('toDate', toDate);
    if (status && status !== 'ALL') params = params.set('status', status);

    return this.http.get<PageResponse<AttendanceRecordResponse>>(this.baseUrl, { params });
  }

  correctAttendance(id: string, request: AttendanceCorrectionRequest): Observable<AttendanceRecordResponse> {
    return this.http.post<AttendanceRecordResponse>(`${this.baseUrl}/${id}/corrections`, request);
  }

  createManualEntry(request: ManualAttendanceRequest): Observable<AttendanceRecordResponse> {
    return this.http.post<AttendanceRecordResponse>(`${this.baseUrl}/manual`, request);
  }

  getRecordAuditHistory(id: string, page = 0, size = 20): Observable<PageResponse<AttendanceCorrectionResponse>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<PageResponse<AttendanceCorrectionResponse>>(`${this.baseUrl}/${id}/audit-history`, { params });
  }

  searchAuditHistory(employeeId?: string, fromDate?: string, toDate?: string, page = 0, size = 20): Observable<PageResponse<AttendanceCorrectionResponse>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (employeeId) params = params.set('employeeId', employeeId);
    if (fromDate) params = params.set('fromDate', fromDate);
    if (toDate) params = params.set('toDate', toDate);

    return this.http.get<PageResponse<AttendanceCorrectionResponse>>(`${this.baseUrl}/audit`, { params });
  }
}
