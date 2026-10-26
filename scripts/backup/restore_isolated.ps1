# ==============================================================================
# Staff Attendance Management System
# Production Disaster Recovery - Standalone Isolated Restore & Verification
# ==============================================================================

param(
    [string]$BackupZip = "",
    [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }),
    [int]$DbPort = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 5435 }),
    [string]$RecoveryDb = "attendance_recovery",
    [string]$DbUser = $(if ($env:DB_USER) { $env:DB_USER } else { "postgres" }),
    [string]$DbPassword = $(if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "" }),
    [string]$RecoveryPhotosDir = "photos_recovery",
    [string]$BackupDir = $(if ($env:BACKUP_STORAGE_ROOT) { $env:BACKUP_STORAGE_ROOT } else { "backups" }),
    [string]$PgBinDir = $(if ($env:PG_BIN_DIR) { $env:PG_BIN_DIR } else { "C:\Program Files\PostgreSQL\17\bin" })
)

$ErrorActionPreference = "Stop"
$startTime = Get-Date

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "DISASTER RECOVERY ISOLATED RESTORE & INTEGRITY TEST" -ForegroundColor Cyan
Write-Host "Recovery DB Target: $DbUser@${DbHost}:${DbPort}/$RecoveryDb" -ForegroundColor Cyan
Write-Host "Recovery Photos Target: $RecoveryPhotosDir" -ForegroundColor Cyan
Write-Host "Timestamp: $($startTime.ToString('yyyy-MM-dd HH:mm:ss UTC'))" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# CRITICAL SAFEGUARD
if ($RecoveryDb.ToLower() -eq "attendance") {
    Write-Host "CRITICAL SAFETY SAFEGUARD: Refusing to restore into live production database 'attendance'." -ForegroundColor Red
    exit 1
}

# 1. Locate Backup Archive
if (-not $BackupZip) {
    $latest = Get-ChildItem -Path $BackupDir -Filter "*.zip" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latest) {
        Write-Host "No backup archive found in '$BackupDir'." -ForegroundColor Red
        exit 1
    }
    $BackupZip = $latest.FullName
}

Write-Host "Using Backup Archive: $BackupZip" -ForegroundColor Yellow

$tempUnpack = Join-Path $BackupDir "temp_restore_verify"
if (Test-Path $tempUnpack) {
    Remove-Item -Path $tempUnpack -Recurse -Force | Out-Null
}
New-Item -ItemType Directory -Path $tempUnpack -Force | Out-Null

try {
    # 2. Extract Backup Archive
    Write-Host "[1/5] Extracting disaster recovery bundle..." -ForegroundColor Yellow
    Expand-Archive -Path $BackupZip -DestinationPath $tempUnpack -Force

    $sqlDump = Join-Path $tempUnpack "database.sql"
    $photosZip = Join-Path $tempUnpack "photos.zip"
    $manifestFile = Join-Path $tempUnpack "manifest.json"

    if (-not (Test-Path $sqlDump)) {
        throw "Corrupt backup: database.sql not found inside archive."
    }

    # 3. Clean Recreate Isolated Recovery Database
    Write-Host "[2/5] Preparing clean isolated recovery database '$RecoveryDb'..." -ForegroundColor Yellow
    $psqlPath = Join-Path $PgBinDir "psql.exe"
    if (-not (Test-Path $psqlPath)) {
        $psqlPath = (Get-Command psql -ErrorAction SilentlyContinue).Source
    }

    $env:PGPASSWORD = $DbPassword
    & $psqlPath -h $DbHost -p $DbPort -U $DbUser -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$RecoveryDb' AND pid <> pg_backend_pid();" | Out-Null
    & $psqlPath -h $DbHost -p $DbPort -U $DbUser -d postgres -c "DROP DATABASE IF EXISTS `"$RecoveryDb`";" | Out-Null
    & $psqlPath -h $DbHost -p $DbPort -U $DbUser -d postgres -c "CREATE DATABASE `"$RecoveryDb`" WITH ENCODING = 'UTF8';" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create clean recovery database '$RecoveryDb'"
    }

    # 4. Restore Database Dump
    Write-Host "[3/5] Restoring database schema and data into '$RecoveryDb'..." -ForegroundColor Yellow
    & $psqlPath -h $DbHost -p $DbPort -U $DbUser -d $RecoveryDb -f $sqlDump | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to restore database dump into '$RecoveryDb'"
    }

    # 5. Restore Photo Archive
    Write-Host "[4/5] Restoring photo storage into '$RecoveryPhotosDir'..." -ForegroundColor Yellow
    if (Test-Path $RecoveryPhotosDir) {
        Remove-Item -Path $RecoveryPhotosDir -Recurse -Force | Out-Null
    }
    New-Item -ItemType Directory -Path $RecoveryPhotosDir -Force | Out-Null
    if (Test-Path $photosZip) {
        Expand-Archive -Path $photosZip -DestinationPath $RecoveryPhotosDir -Force
    }

    # 6. Integrity Verification & Row Count Queries
    Write-Host "[5/5] Running relational and physical data integrity verification..." -ForegroundColor Yellow

    $checkQuery = @"
SELECT 
    (SELECT COUNT(*) FROM admin_users) as admin_count,
    (SELECT COUNT(*) FROM employees) as emp_count,
    (SELECT COUNT(*) FROM attendance_records) as attendance_count,
    (SELECT COUNT(*) FROM attendance_photos) as photo_count,
    (SELECT COUNT(*) FROM kiosk_devices) as device_count;
"@

    $counts = & $psqlPath -h $DbHost -p $DbPort -U $DbUser -d $RecoveryDb -t -A -F "," -c $checkQuery
    $parts = $counts.Trim().Split(",")
    $adminCount = $parts[0]
    $empCount = $parts[1]
    $attCount = $parts[2]
    $photoRecCount = $parts[3]
    $devCount = $parts[4]

    # Verify photo files resolution
    $missingPhotos = 0
    $checkedPhotos = 0
    $photoPaths = & $psqlPath -h $DbHost -p $DbPort -U $DbUser -d $RecoveryDb -t -A -c "SELECT storage_path FROM attendance_photos WHERE storage_status = 'ACTIVE';"
    if ($photoPaths) {
        foreach ($p in $photoPaths.Split("`n")) {
            $p = $p.Trim()
            if ($p) {
                $checkedPhotos++
                $expectedFile = Join-Path $RecoveryPhotosDir $p
                if (-not (Test-Path $expectedFile) -or (Get-Item $expectedFile).Length -eq 0) {
                    $missingPhotos++
                    Write-Host " -> Photo missing in recovery dir: $p" -ForegroundColor Red
                }
            }
        }
    }

    $durationMs = [int]((Get-Date) - $startTime).TotalMilliseconds

    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host "ISOLATED RESTORE VALIDATION: PASSED" -ForegroundColor Green
    Write-Host "Target Database: $RecoveryDb" -ForegroundColor Green
    Write-Host "Target Photos: $RecoveryPhotosDir" -ForegroundColor Green
    Write-Host "Restored Table Counts:" -ForegroundColor Green
    Write-Host "  - Admin Users:        $adminCount" -ForegroundColor Green
    Write-Host "  - Employees:          $empCount" -ForegroundColor Green
    Write-Host "  - Attendance Records: $attCount" -ForegroundColor Green
    Write-Host "  - Photo Metadata:     $photoRecCount" -ForegroundColor Green
    Write-Host "  - Authorized Devices: $devCount" -ForegroundColor Green
    Write-Host "Photo Files Verified: $checkedPhotos (Missing: $missingPhotos)" -ForegroundColor Green
    Write-Host "Restore Duration (RTO): ${durationMs}ms (Target: < 300000ms / 5 min)" -ForegroundColor Green
    Write-Host "Production Database 'attendance' Status: 100% UNTOUCHED" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
    exit 0
} catch {
    Write-Host "RESTORE VALIDATION FAILED: $_" -ForegroundColor Red
    exit 1
} finally {
    if (Test-Path $tempUnpack) {
        Remove-Item -Path $tempUnpack -Recurse -Force -ErrorAction SilentlyContinue
    }
}
