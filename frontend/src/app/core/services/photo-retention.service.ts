import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  PhotoRetentionPolicy,
  PhotoRetentionPolicyRequest,
  StorageHealth,
  RetentionDryRun,
  CleanupExecutionResult,
  StorageOrphanScan
} from '../models/photo-retention.model';

@Injectable({
  providedIn: 'root'
})
export class PhotoRetentionService {
  private readonly baseUrl = '/api/admin/storage';

  constructor(private http: HttpClient) {}

  getHealth(): Observable<StorageHealth> {
    return this.http.get<StorageHealth>(`${this.baseUrl}/health`);
  }

  getPolicy(): Observable<PhotoRetentionPolicy> {
    return this.http.get<PhotoRetentionPolicy>(`${this.baseUrl}/retention/policy`);
  }

  updatePolicy(request: PhotoRetentionPolicyRequest): Observable<PhotoRetentionPolicy> {
    return this.http.put<PhotoRetentionPolicy>(`${this.baseUrl}/retention/policy`, request);
  }

  runDryRun(): Observable<RetentionDryRun> {
    return this.http.get<RetentionDryRun>(`${this.baseUrl}/retention/dry-run`);
  }

  executeCleanup(): Observable<CleanupExecutionResult> {
    return this.http.post<CleanupExecutionResult>(`${this.baseUrl}/cleanup/execute`, {});
  }

  scanOrphans(): Observable<StorageOrphanScan> {
    return this.http.get<StorageOrphanScan>(`${this.baseUrl}/orphans/scan`);
  }

  getCleanupHistory(limit: number = 20): Observable<CleanupExecutionResult[]> {
    return this.http.get<CleanupExecutionResult[]>(`${this.baseUrl}/cleanup/history?limit=${limit}`);
  }
}
