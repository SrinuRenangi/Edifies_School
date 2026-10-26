import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap } from 'rxjs';
import { AdminLoginRequest, AdminLoginResponse, AdminProfileResponse, UserRole } from '../models/auth.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly baseUrl = '/api/auth/admin';
  private currentUserSubject = new BehaviorSubject<AdminProfileResponse | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {}

  public get currentUserValue(): AdminProfileResponse | null {
    return this.currentUserSubject.value;
  }

  public get isAuthenticated(): boolean {
    return this.currentUserSubject.value !== null;
  }

  public get currentRole(): UserRole | null {
    return this.currentUserSubject.value?.role || null;
  }

  public get isAdmin(): boolean {
    return this.currentRole === 'ROLE_ADMIN';
  }

  public get isManager(): boolean {
    return this.currentRole === 'ROLE_MANAGER';
  }

  public hasRole(role: UserRole): boolean {
    return this.currentRole === role;
  }

  login(credentials: AdminLoginRequest): Observable<AdminLoginResponse> {
    return this.http.post<AdminLoginResponse>(`${this.baseUrl}/login`, credentials, { withCredentials: true })
      .pipe(
        tap(res => {
          this.currentUserSubject.next({
            username: res.username,
            fullName: res.fullName,
            role: res.role
          });
        })
      );
  }

  logout(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/logout`, {}, { withCredentials: true })
      .pipe(
        tap(() => {
          this.currentUserSubject.next(null);
        })
      );
  }

  checkSession(): Observable<AdminProfileResponse> {
    return this.http.get<AdminProfileResponse>(`${this.baseUrl}/me`, { withCredentials: true })
      .pipe(
        tap(profile => {
          this.currentUserSubject.next(profile);
        })
      );
  }
}
