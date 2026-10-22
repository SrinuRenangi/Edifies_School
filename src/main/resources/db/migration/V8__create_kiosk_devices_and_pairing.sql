-- Phase 11: Kiosk Registration, Secure Device Pairing, Device Authentication, and Access Control

-- 1. Kiosk Devices Table
CREATE TABLE kiosk_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_name VARCHAR(100) NOT NULL,
    device_identifier VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    device_token_hash VARCHAR(128),
    paired_at TIMESTAMP WITH TIME ZONE,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(255),
    created_by VARCHAR(100),
    revoked_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_kiosk_device_status CHECK (status IN ('PENDING_PAIRING', 'ACTIVE', 'REVOKED'))
);

CREATE INDEX idx_kiosk_devices_status ON kiosk_devices (status);
CREATE INDEX idx_kiosk_devices_token_hash ON kiosk_devices (device_token_hash) WHERE device_token_hash IS NOT NULL;
CREATE INDEX idx_kiosk_devices_identifier ON kiosk_devices (device_identifier);

-- 2. Kiosk Pairing Codes Table
CREATE TABLE kiosk_pairing_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_name VARCHAR(100) NOT NULL,
    pairing_code_hash VARCHAR(128) NOT NULL UNIQUE,
    created_by VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    failed_attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    paired_device_id UUID REFERENCES kiosk_devices(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_kiosk_pairing_status CHECK (status IN ('PENDING', 'USED', 'EXPIRED', 'CANCELLED'))
);

CREATE INDEX idx_kiosk_pairing_code_hash ON kiosk_pairing_codes (pairing_code_hash);
CREATE INDEX idx_kiosk_pairing_status_expires ON kiosk_pairing_codes (status, expires_at);

-- 3. Kiosk Device Audit Logs Table
CREATE TABLE kiosk_device_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID REFERENCES kiosk_devices(id) ON DELETE SET NULL,
    device_name VARCHAR(100),
    event_type VARCHAR(50) NOT NULL,
    actor VARCHAR(100),
    ip_address VARCHAR(50),
    user_agent VARCHAR(255),
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kiosk_device_audit_device_id ON kiosk_device_audit_logs (device_id);
CREATE INDEX idx_kiosk_device_audit_created_at ON kiosk_device_audit_logs (created_at DESC);
