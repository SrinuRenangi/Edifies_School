import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  DashboardSummary,
  DashboardQuickSearchEmployee
} from '../models/dashboard.model';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private readonly baseUrl = '/api/admin/dashboard';

  constructor(private http: HttpClient) {}

  /**
   * Retrieves dashboard overview statistics, recent activities, and attention-required items.
   */
  getSummary(workDate?: string): Observable<DashboardSummary> {
    let params = new HttpParams();
    if (workDate) {
      params = params.set('workDate', workDate);
    }
    return this.http.get<DashboardSummary>(`${this.baseUrl}/summary`, {
      params,
      withCredentials: true
    });
  }

  /**
   * Quick search for active employees with today's attendance status.
   */
  searchEmployees(query?: string, workDate?: string): Observable<DashboardQuickSearchEmployee[]> {
    let params = new HttpParams();
    if (query) {
      params = params.set('query', query);
    }
    if (workDate) {
      params = params.set('workDate', workDate);
    }
    return this.http.get<DashboardQuickSearchEmployee[]>(`${this.baseUrl}/employee-search`, {
      params,
      withCredentials: true
    });
  }
}
