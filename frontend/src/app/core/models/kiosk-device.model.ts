export type KioskDeviceStatus = 'PENDING_PAIRING' | 'ACTIVE' | 'REVOKED';

export type PairingCodeStatus = 'PENDING' | 'USED' | 'EXPIRED' | 'CANCELLED';

export type DeviceAuditEventType =
  | 'PAIRING_INITIATED'
  | 'PAIRING_COMPLETED'
  | 'PAIRING_FAILED'
  | 'PAIRING_CANCELLED'
  | 'DEVICE_REVOKED'
  | 'DEVICE_AUTH_FAILED'
  | 'DEVICE_ACTIVITY';

export interface InitiatePairingRequest {
  deviceName: string;
}

export interface InitiatePairingResponse {
  pairingId: string;
  deviceName: string;
  pairingCode: string;
  expiresAt: string;
  expiresInSeconds: number;
}

export interface CompletePairingRequest {
  pairingCode: string;
  clientInfo?: string;
}

export interface CompletePairingResponse {
  deviceId: string;
  deviceIdentifier: string;
  deviceName: string;
  deviceToken: string;
  status: KioskDeviceStatus;
  pairedAt: string;
}

export interface KioskDevice {
  id: string;
  deviceIdentifier: string;
  deviceName: string;
  status: KioskDeviceStatus;
  pairedAt: string | null;
  lastSeenAt: string | null;
  revokedAt: string | null;
  revocationReason: string | null;
  createdBy: string | null;
  revokedBy: string | null;
  createdAt: string;
}

export interface RevokeDeviceRequest {
  reason?: string;
}

export interface DeviceStatusResponse {
  registered: boolean;
  deviceId: string | null;
  deviceIdentifier: string | null;
  deviceName: string | null;
  status: string;
}

export interface DeviceAuditLog {
  id: string;
  deviceId: string | null;
  deviceName: string | null;
  eventType: DeviceAuditEventType;
  actor: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  details: string | null;
  createdAt: string;
}
