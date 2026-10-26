export type UserRole = 'ROLE_ADMIN' | 'ROLE_MANAGER';

export interface AdminUser {
  username: string;
  fullName: string;
  role: UserRole;
}

export interface AdminLoginRequest {
  username: string;
  password: string;
}

export interface AdminLoginResponse {
  username: string;
  fullName: string;
  role: UserRole;
  message: string;
}

export interface AdminProfileResponse {
  username: string;
  fullName: string;
  role: UserRole;
}
