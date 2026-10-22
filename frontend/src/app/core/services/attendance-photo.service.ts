import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PhotoType } from '../models/attendance-photo.model';

@Injectable({
  providedIn: 'root'
})
export class AttendancePhotoService {
  private readonly kioskBaseUrl = '/api/kiosk/attendance';
  private readonly adminBaseUrl = '/api/admin/attendance';

  constructor(private http: HttpClient) {}

  /**
   * Executes check-in with captured photo evidence.
   */
  checkInWithPhoto(kioskSessionToken: string, photoBlob: Blob, idempotencyKey: string): Observable<any> {
    const formData = new FormData();
    formData.append('photo', photoBlob, 'check-in.jpg');

    const headers = new HttpHeaders({
      'X-Kiosk-Session': kioskSessionToken,
      'Idempotency-Key': idempotencyKey
    });

    return this.http.post(`${this.kioskBaseUrl}/check-in`, formData, { headers });
  }

  /**
   * Executes check-out with captured photo evidence.
   */
  checkOutWithPhoto(kioskSessionToken: string, photoBlob: Blob, idempotencyKey: string): Observable<any> {
    const formData = new FormData();
    formData.append('photo', photoBlob, 'check-out.jpg');

    const headers = new HttpHeaders({
      'X-Kiosk-Session': kioskSessionToken,
      'Idempotency-Key': idempotencyKey
    });

    return this.http.post(`${this.kioskBaseUrl}/check-out`, formData, { headers });
  }

  /**
   * Retrieves an authorized binary photo blob for administrative display.
   */
  getAdminPhotoBlob(attendanceId: string, photoType: PhotoType): Observable<Blob> {
    return this.http.get(`${this.adminBaseUrl}/${attendanceId}/photo/${photoType}`, {
      responseType: 'blob',
      withCredentials: true
    });
  }
}
