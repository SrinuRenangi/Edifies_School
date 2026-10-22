import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  KioskDevice,
  InitiatePairingRequest,
  InitiatePairingResponse,
  CompletePairingRequest,
  CompletePairingResponse,
  RevokeDeviceRequest,
  DeviceStatusResponse,
  DeviceAuditLog
} from '../models/kiosk-device.model';

@Injectable({
  providedIn: 'root'
})
export class KioskDeviceService {
  private readonly adminUrl = '/api/admin/devices';
  private readonly kioskUrl = '/api/kiosk/devices';
  private readonly TOKEN_STORAGE_KEY = 'kiosk_device_token';
  private readonly DEVICE_INFO_KEY = 'kiosk_device_info';

  constructor(private http: HttpClient) {}

  // ================= ADMIN DEVICE MANAGEMENT =================

  getAllDevices(): Observable<KioskDevice[]> {
    return this.http.get<KioskDevice[]>(this.adminUrl);
  }

  initiatePairing(request: InitiatePairingRequest): Observable<InitiatePairingResponse> {
    return this.http.post<InitiatePairingResponse>(`${this.adminUrl}/pair/initiate`, request);
  }

  cancelPairing(pairingId: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.adminUrl}/pair/${pairingId}/cancel`, {});
  }

  revokeDevice(deviceId: string, request?: RevokeDeviceRequest): Observable<KioskDevice> {
    return this.http.post<KioskDevice>(`${this.adminUrl}/${deviceId}/revoke`, request || {});
  }

  getDeviceAuditLogs(): Observable<DeviceAuditLog[]> {
    return this.http.get<DeviceAuditLog[]>(`${this.adminUrl}/audit`);
  }

  // ================= KIOSK WORKSTATION PAIRING =================

  pairDevice(request: CompletePairingRequest): Observable<CompletePairingResponse> {
    return this.http.post<CompletePairingResponse>(`${this.kioskUrl}/pair`, request);
  }

  getDeviceStatus(): Observable<DeviceStatusResponse> {
    const headers = this.getDeviceHeaders();
    return this.http.get<DeviceStatusResponse>(`${this.kioskUrl}/status`, { headers });
  }

  // ================= LOCAL CREDENTIAL STORAGE =================

  getStoredDeviceToken(): string | null {
    return localStorage.getItem(this.TOKEN_STORAGE_KEY);
  }

  storeDeviceCredentials(response: CompletePairingResponse): void {
    localStorage.setItem(this.TOKEN_STORAGE_KEY, response.deviceToken);
    localStorage.setItem(
      this.DEVICE_INFO_KEY,
      JSON.stringify({
        deviceId: response.deviceId,
        deviceIdentifier: response.deviceIdentifier,
        deviceName: response.deviceName,
        pairedAt: response.pairedAt
      })
    );
  }

  clearStoredDeviceCredentials(): void {
    localStorage.removeItem(this.TOKEN_STORAGE_KEY);
    localStorage.removeItem(this.DEVICE_INFO_KEY);
  }

  getStoredDeviceInfo(): { deviceId: string; deviceIdentifier: string; deviceName: string; pairedAt: string } | null {
    const raw = localStorage.getItem(this.DEVICE_INFO_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  isDevicePaired(): boolean {
    return !!this.getStoredDeviceToken();
  }

  private getDeviceHeaders(): HttpHeaders {
    let headers = new HttpHeaders();
    const token = this.getStoredDeviceToken();
    if (token) {
      headers = headers.set('X-Kiosk-Device-Token', token);
    }
    return headers;
  }
}
