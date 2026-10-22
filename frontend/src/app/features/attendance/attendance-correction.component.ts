import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AttendanceCorrectionService } from '../../core/services/attendance-correction.service';
import { EmployeeService } from '../../core/services/employee.service';
import { AttendanceRecordResponse } from '../../core/models/attendance-record.model';
import {
  AttendanceCorrectionRequest,
  AttendanceCorrectionResponse,
  CorrectionActionType,
  ManualAttendanceRequest
} from '../../core/models/attendance-correction.model';
import { Employee } from '../../core/models/employee.model';

@Component({
  selector: 'app-attendance-correction',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="correction-container">
      <header class="page-header">
        <div>
          <h2>Attendance Corrections & Audit Log</h2>
          <p class="subtitle">Review punches, correct discrepancies, record missed checkouts, and inspect immutable audit history</p>
        </div>
        <div class="header-actions">
          <button class="btn btn-outline" (click)="loadGlobalAudit()">
            Audit Timeline
          </button>
          <button class="btn btn-primary" (click)="openManualEntryModal()">
            + Manual Entry
          </button>
        </div>
      </header>

      <!-- Filter Controls -->
      <div class="toolbar">
        <input
          type="text"
          class="form-control search-input"
          placeholder="Search by staff code, name, department..."
          [(ngModel)]="searchQuery"
          (ngModelChange)="onSearchChange()"
        />
        <input
          type="date"
          class="form-control date-input"
          [(ngModel)]="fromDate"
          (change)="loadRecords()"
          title="From Work Date"
        />
        <input
          type="date"
          class="form-control date-input"
          [(ngModel)]="toDate"
          (change)="loadRecords()"
          title="To Work Date"
        />
        <select class="form-control filter-select" [(ngModel)]="statusFilter" (change)="loadRecords()">
          <option value="ALL">All Statuses</option>
          <option value="CHECKED_IN">Incomplete / Open (Checked In)</option>
          <option value="CHECKED_OUT">Completed (Checked Out)</option>
          <option value="AUTO_CLOSED">Auto-Closed (Missed Punch)</option>
          <option value="VOIDED">Voided / Superseded</option>
        </select>
        <button class="btn btn-secondary btn-sm" (click)="resetFilters()">Reset</button>
      </div>

      <!-- Notification Alerts -->
      <div *ngIf="successMessage()" class="alert alert-success">
        {{ successMessage() }}
      </div>
      <div *ngIf="errorMessage()" class="alert alert-danger">
        {{ errorMessage() }}
      </div>

      <!-- Loading State -->
      <div *ngIf="isLoading()" class="state-container">
        <div class="spinner"></div>
        <p>Loading attendance records...</p>
      </div>

      <!-- Attendance Table -->
      <div *ngIf="!isLoading()" class="table-card">
        <table class="data-table">
          <thead>
            <tr>
              <th>Work Date</th>
              <th>Staff Member</th>
              <th>Department</th>
              <th>Status</th>
              <th>Check-In (IST)</th>
              <th>Check-Out (IST)</th>
              <th>Duration</th>
              <th>Type</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let record of records()" [class.row-voided]="record.status === 'VOIDED'">
              <td><strong>{{ record.workDate }}</strong></td>
              <td>
                <div class="employee-cell">
                  <span class="emp-name">{{ record.employeeName }}</span>
                  <span class="emp-code">{{ record.employeeCode }}</span>
                </div>
              </td>
              <td>{{ record.department }}</td>
              <td>
                <span class="status-badge" [ngClass]="getStatusClass(record.status)">
                  {{ record.status }}
                </span>
              </td>
              <td>{{ formatTime(record.checkInTime) }}</td>
              <td>
                <span *ngIf="record.checkOutTime">{{ formatTime(record.checkOutTime) }}</span>
                <span *ngIf="!record.checkOutTime" class="badge-incomplete">Missed / Open</span>
              </td>
              <td>{{ record.durationFormatted || '—' }}</td>
              <td>
                <span class="indicator-badge" [ngClass]="getIndicatorClass(record.correctionIndicator)">
                  {{ record.correctionIndicator || 'Original' }}
                </span>
              </td>
              <td class="action-cell">
                <button
                  *ngIf="record.status !== 'VOIDED'"
                  class="btn-icon btn-action-edit"
                  (click)="openCorrectionModal(record)"
                  title="Correct attendance"
                >
                  Correct
                </button>
                <button
                  class="btn-icon btn-action-audit"
                  (click)="openAuditHistoryModal(record)"
                  title="Inspect audit trail"
                >
                  Audit ({{ record.id.substring(0, 4) }})
                </button>
              </td>
            </tr>
            <tr *ngIf="records().length === 0">
              <td colspan="9" class="empty-cell">No attendance records match the selected filters.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- ================= CORRECTION MODAL ================= -->
      <div *ngIf="showCorrectionModal()" class="modal-overlay">
        <div class="modal-card">
          <div class="modal-header">
            <h3>Attendance Correction</h3>
            <button class="btn-close" (click)="closeCorrectionModal()">&times;</button>
          </div>

          <div class="modal-body" *ngIf="selectedRecord">
            <!-- Authoritative Before-State Box -->
            <div class="snapshot-box">
              <h4>Current Authoritative State</h4>
              <div class="snapshot-grid">
                <div><span>Employee:</span> <strong>{{ selectedRecord.employeeName }} ({{ selectedRecord.employeeCode }})</strong></div>
                <div><span>Work Date:</span> <strong>{{ selectedRecord.workDate }}</strong></div>
                <div><span>Check-In:</span> <strong>{{ formatTime(selectedRecord.checkInTime) }}</strong></div>
                <div><span>Check-Out:</span> <strong>{{ selectedRecord.checkOutTime ? formatTime(selectedRecord.checkOutTime) : 'Not recorded (Open / Missed)' }}</strong></div>
                <div><span>Status:</span> <span class="status-badge" [ngClass]="getStatusClass(selectedRecord.status)">{{ selectedRecord.status }}</span></div>
                <div><span>Record Version:</span> <code>v{{ selectedRecord.version }}</code></div>
              </div>
            </div>

            <!-- Choose Operation -->
            <div class="form-group">
              <label>Select Correction Workflow</label>
              <select class="form-control" [(ngModel)]="correctionAction" (change)="onActionTypeChange()">
                <option value="CORRECT_CHECK_IN">Correct Check-In Time</option>
                <option value="CORRECT_CHECK_OUT">Correct Check-Out Time</option>
                <option value="CORRECT_BOTH">Correct Both Check-In and Check-Out</option>
                <option value="RESOLVE_MISSED_CHECKOUT" *ngIf="selectedRecord.isIncomplete || !selectedRecord.checkOutTime">
                  Resolve Missed Check-Out
                </option>
                <option value="VOID_RECORD">Void / Mark Record as Erroneous</option>
              </select>
            </div>

            <!-- Dynamic Inputs based on Action -->
            <div *ngIf="correctionAction !== 'VOID_RECORD'" class="inputs-grid">
              <div class="form-group" *ngIf="correctionAction === 'CORRECT_CHECK_IN' || correctionAction === 'CORRECT_BOTH'">
                <label>Verified Check-In Time (IST) *</label>
                <input type="datetime-local" class="form-control" [(ngModel)]="inputCheckIn" />
              </div>
              <div class="form-group" *ngIf="correctionAction === 'CORRECT_CHECK_OUT' || correctionAction === 'CORRECT_BOTH' || correctionAction === 'RESOLVE_MISSED_CHECKOUT'">
                <label>Verified Check-Out Time (IST) *</label>
                <input type="datetime-local" class="form-control" [(ngModel)]="inputCheckOut" />
              </div>
            </div>

            <!-- Mandatory Reason -->
            <div class="form-group">
              <label>Mandatory Correction Reason * (Required for immutable audit history)</label>
              <textarea
                class="form-control"
                rows="3"
                maxlength="500"
                placeholder="e.g. Employee forgot to check out; departure verified by school supervisor."
                [(ngModel)]="correctionReason"
              ></textarea>
              <small class="text-muted">{{ correctionReason.length }}/500 characters</small>
            </div>

            <!-- Review / Confirmation Comparison -->
            <div *ngIf="isReviewStep" class="review-box">
              <h4>Review Proposed Changes Before Committing</h4>
              <table class="review-table">
                <thead>
                  <tr>
                    <th>Field</th>
                    <th>Before (Current)</th>
                    <th>After (Proposed)</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngIf="correctionAction === 'CORRECT_CHECK_IN' || correctionAction === 'CORRECT_BOTH'">
                    <td>Check-In</td>
                    <td>{{ formatTime(selectedRecord.checkInTime) }}</td>
                    <td class="text-highlight">{{ inputCheckIn ? (inputCheckIn | date:'yyyy-MM-dd HH:mm:ss') : '—' }}</td>
                  </tr>
                  <tr *ngIf="correctionAction === 'CORRECT_CHECK_OUT' || correctionAction === 'CORRECT_BOTH' || correctionAction === 'RESOLVE_MISSED_CHECKOUT'">
                    <td>Check-Out</td>
                    <td>{{ selectedRecord.checkOutTime ? formatTime(selectedRecord.checkOutTime) : '—' }}</td>
                    <td class="text-highlight">{{ inputCheckOut ? (inputCheckOut | date:'yyyy-MM-dd HH:mm:ss') : '—' }}</td>
                  </tr>
                  <tr *ngIf="correctionAction === 'VOID_RECORD'">
                    <td>Status</td>
                    <td>{{ selectedRecord.status }}</td>
                    <td class="text-danger">VOIDED</td>
                  </tr>
                  <tr>
                    <td>Reason</td>
                    <td colspan="2"><em>"{{ correctionReason }}"</em></td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div *ngIf="modalError" class="alert alert-danger">
              {{ modalError }}
            </div>
          </div>

          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="closeCorrectionModal()">Cancel</button>
            <button *ngIf="!isReviewStep" class="btn btn-outline" (click)="proceedToReview()">
              Review Proposed Change &rarr;
            </button>
            <button *ngIf="isReviewStep" class="btn btn-primary" [disabled]="isSubmitting" (click)="submitCorrection()">
              {{ isSubmitting ? 'Committing...' : 'Confirm & Commit Correction' }}
            </button>
          </div>
        </div>
      </div>

      <!-- ================= AUDIT HISTORY MODAL ================= -->
      <div *ngIf="showAuditModal()" class="modal-overlay">
        <div class="modal-card modal-lg">
          <div class="modal-header">
            <h3>Audit History & Correction Timeline</h3>
            <button class="btn-close" (click)="closeAuditModal()">&times;</button>
          </div>
          <div class="modal-body">
            <p *ngIf="selectedRecord" class="text-muted mb-3">
              Inspecting immutable audit history for <strong>{{ selectedRecord.employeeName }}</strong> on work date <strong>{{ selectedRecord.workDate }}</strong>
            </p>

            <div *ngIf="auditLoading" class="state-container">
              <div class="spinner"></div>
              <p>Retrieving chronological audit trail...</p>
            </div>

            <div *ngIf="!auditLoading && auditLogs.length === 0" class="empty-cell">
              No correction events recorded for this attendance entry. This record contains its original unedited values.
            </div>

            <div *ngIf="!auditLoading && auditLogs.length > 0" class="timeline">
              <div *ngFor="let audit of auditLogs" class="timeline-item">
                <div class="timeline-badge"></div>
                <div class="timeline-content">
                  <div class="timeline-header">
                    <span class="action-tag">{{ audit.actionType }}</span>
                    <span class="performed-by">by <strong>{{ audit.performedBy }}</strong></span>
                    <span class="timestamp">{{ audit.createdAt | date:'yyyy-MM-dd HH:mm:ss':'Asia/Kolkata' }} IST</span>
                  </div>
                  <p class="audit-reason"><strong>Reason:</strong> "{{ audit.reason }}"</p>

                  <div class="audit-diff-grid">
                    <div *ngIf="audit.previousCheckInTime || audit.correctedCheckInTime">
                      <span class="diff-lbl">Check-In:</span>
                      <span class="diff-prev">{{ formatTime(audit.previousCheckInTime) }}</span>
                      &rarr;
                      <span class="diff-curr">{{ formatTime(audit.correctedCheckInTime) }}</span>
                    </div>
                    <div *ngIf="audit.previousCheckOutTime || audit.correctedCheckOutTime">
                      <span class="diff-lbl">Check-Out:</span>
                      <span class="diff-prev">{{ formatTime(audit.previousCheckOutTime) }}</span>
                      &rarr;
                      <span class="diff-curr">{{ formatTime(audit.correctedCheckOutTime) }}</span>
                    </div>
                    <div>
                      <span class="diff-lbl">Status:</span>
                      <span class="diff-prev">{{ audit.previousStatus || '—' }}</span>
                      &rarr;
                      <span class="diff-curr">{{ audit.correctedStatus }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="closeAuditModal()">Close</button>
          </div>
        </div>
      </div>

      <!-- ================= MANUAL ATTENDANCE MODAL ================= -->
      <div *ngIf="showManualModal()" class="modal-overlay">
        <div class="modal-card">
          <div class="modal-header">
            <h3>Create Authorized Manual Attendance</h3>
            <button class="btn-close" (click)="closeManualModal()">&times;</button>
          </div>
          <div class="modal-body">
            <p class="text-muted">
              Record a verified attendance entry directly into the authoritative register. A mandatory audit event will be recorded under your administrator account.
            </p>

            <div class="form-group">
              <label>Select Staff Member *</label>
              <select class="form-control" [(ngModel)]="manualEmployeeId">
                <option value="">-- Choose Employee --</option>
                <option *ngFor="let emp of allEmployees" [value]="emp.id">
                  {{ emp.fullName }} ({{ emp.employeeId }} - {{ emp.department }})
                </option>
              </select>
            </div>

            <div class="form-group">
              <label>Applicable Work Date *</label>
              <input type="date" class="form-control" [(ngModel)]="manualWorkDate" />
            </div>

            <div class="inputs-grid">
              <div class="form-group">
                <label>Verified Check-In Time (IST) *</label>
                <input type="datetime-local" class="form-control" [(ngModel)]="manualCheckIn" />
              </div>
              <div class="form-group">
                <label>Verified Check-Out Time (IST)</label>
                <input type="datetime-local" class="form-control" [(ngModel)]="manualCheckOut" />
              </div>
            </div>

            <div class="form-group">
              <label>Documented Reason for Manual Entry *</label>
              <textarea
                class="form-control"
                rows="3"
                maxlength="500"
                placeholder="e.g. Employee on field duty; manual entry approved after verifying signed logsheet."
                [(ngModel)]="manualReason"
              ></textarea>
              <small class="text-muted">{{ manualReason.length }}/500 characters</small>
            </div>

            <div *ngIf="manualError" class="alert alert-danger">
              {{ manualError }}
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="closeManualModal()">Cancel</button>
            <button class="btn btn-primary" [disabled]="isSubmitting" (click)="submitManualEntry()">
              {{ isSubmitting ? 'Saving...' : 'Create Manual Entry' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .correction-container {
      padding: 1.5rem;
      max-width: 1400px;
      margin: 0 auto;
      font-family: 'Inter', sans-serif;
    }
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
    }
    .page-header h2 {
      margin: 0;
      font-size: 1.6rem;
      font-weight: 700;
      color: #0f172a;
    }
    .subtitle {
      color: #64748b;
      margin-top: 0.25rem;
      font-size: 0.95rem;
    }
    .header-actions {
      display: flex;
      gap: 0.75rem;
    }
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      align-items: center;
      background: #ffffff;
      padding: 1rem;
      border-radius: 8px;
      border: 1px solid #e2e8f0;
      margin-bottom: 1.5rem;
    }
    .form-control {
      padding: 0.5rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.9rem;
    }
    .search-input {
      flex: 1;
      min-width: 250px;
    }
    .date-input {
      width: 160px;
    }
    .filter-select {
      width: 220px;
    }
    .table-card {
      background: #ffffff;
      border-radius: 8px;
      border: 1px solid #e2e8f0;
      overflow-x: auto;
    }
    .data-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.9rem;
    }
    .data-table th {
      background: #f8fafc;
      padding: 0.75rem 1rem;
      font-weight: 600;
      color: #475569;
      border-bottom: 1px solid #e2e8f0;
    }
    .data-table td {
      padding: 0.75rem 1rem;
      border-bottom: 1px solid #f1f5f9;
      color: #1e293b;
    }
    .row-voided {
      background-color: #fff1f2;
      opacity: 0.75;
    }
    .employee-cell {
      display: flex;
      flex-direction: column;
    }
    .emp-name {
      font-weight: 600;
      color: #0f172a;
    }
    .emp-code {
      font-size: 0.8rem;
      color: #64748b;
    }
    .status-badge {
      display: inline-block;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
    }
    .status-checked-in { background: #e0f2fe; color: #0369a1; }
    .status-checked-out { background: #dcfce7; color: #15803d; }
    .status-auto-closed { background: #fef3c7; color: #b45309; }
    .status-voided { background: #fee2e2; color: #b91c1c; }
    .badge-incomplete {
      color: #dc2626;
      font-size: 0.8rem;
      font-weight: 500;
    }
    .indicator-badge {
      padding: 0.2rem 0.45rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 500;
    }
    .indicator-original { background: #f1f5f9; color: #475569; }
    .indicator-corrected { background: #fef08a; color: #854d0e; font-weight: 600; }
    .indicator-manual { background: #e0e7ff; color: #3730a3; font-weight: 600; }
    .action-cell {
      display: flex;
      gap: 0.5rem;
    }
    .btn-icon {
      padding: 0.35rem 0.65rem;
      border-radius: 4px;
      font-size: 0.8rem;
      cursor: pointer;
      border: 1px solid transparent;
      font-weight: 500;
    }
    .btn-action-edit {
      background: #eff6ff;
      color: #1d4ed8;
      border-color: #bfdbfe;
    }
    .btn-action-audit {
      background: #f8fafc;
      color: #334155;
      border-color: #cbd5e1;
    }
    .btn {
      padding: 0.5rem 1rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
      border: 1px solid transparent;
    }
    .btn-primary { background: #2563eb; color: #fff; }
    .btn-primary:hover { background: #1d4ed8; }
    .btn-secondary { background: #e2e8f0; color: #334155; }
    .btn-outline { background: #fff; border: 1px solid #cbd5e1; color: #334155; }
    .modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.55);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      backdrop-filter: blur(2px);
    }
    .modal-card {
      background: #ffffff;
      border-radius: 10px;
      width: 100%;
      max-width: 650px;
      max-height: 90vh;
      overflow-y: auto;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
    }
    .modal-lg { max-width: 800px; }
    .modal-header {
      padding: 1.25rem;
      border-bottom: 1px solid #e2e8f0;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .modal-header h3 { margin: 0; font-size: 1.25rem; font-weight: 700; color: #0f172a; }
    .btn-close { background: none; border: none; font-size: 1.5rem; cursor: pointer; color: #94a3b8; }
    .modal-body { padding: 1.25rem; }
    .modal-footer {
      padding: 1rem 1.25rem;
      border-top: 1px solid #e2e8f0;
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      background: #f8fafc;
    }
    .snapshot-box {
      background: #f8fafc;
      padding: 1rem;
      border-radius: 6px;
      border: 1px solid #e2e8f0;
      margin-bottom: 1rem;
    }
    .snapshot-box h4 { margin: 0 0 0.5rem 0; font-size: 0.85rem; color: #64748b; text-transform: uppercase; }
    .snapshot-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.5rem;
      font-size: 0.85rem;
    }
    .inputs-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }
    .form-group { margin-bottom: 1rem; }
    .form-group label { display: block; font-size: 0.85rem; font-weight: 600; margin-bottom: 0.35rem; color: #334155; }
    .review-box {
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      padding: 1rem;
      border-radius: 6px;
      margin-top: 1rem;
    }
    .review-table { width: 100%; font-size: 0.85rem; border-collapse: collapse; }
    .review-table th, .review-table td { padding: 0.4rem 0.6rem; border-bottom: 1px solid #dcfce7; text-align: left; }
    .text-highlight { color: #15803d; font-weight: 700; }
    .timeline { display: flex; flex-direction: column; gap: 1rem; margin-top: 1rem; }
    .timeline-item {
      display: flex;
      gap: 1rem;
      border-left: 2px solid #3b82f6;
      padding-left: 1rem;
      position: relative;
    }
    .timeline-badge {
      position: absolute;
      left: -6px;
      top: 4px;
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: #3b82f6;
    }
    .timeline-content {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 0.75rem 1rem;
      width: 100%;
    }
    .timeline-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.5rem;
      font-size: 0.8rem;
    }
    .action-tag {
      background: #dbeafe;
      color: #1e40af;
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
      font-weight: 600;
    }
    .timestamp { margin-left: auto; color: #64748b; }
    .audit-reason { margin: 0.25rem 0 0.5rem 0; font-size: 0.85rem; color: #1e293b; }
    .audit-diff-grid { font-size: 0.8rem; color: #475569; display: flex; flex-direction: column; gap: 0.25rem; }
    .diff-prev { text-decoration: line-through; color: #94a3b8; }
    .diff-curr { color: #059669; font-weight: 600; }
    .alert { padding: 0.75rem 1rem; border-radius: 6px; margin-bottom: 1rem; font-size: 0.85rem; }
    .alert-success { background: #dcfce7; color: #166534; border: 1px solid #bbf7d0; }
    .alert-danger { background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; }
    .empty-cell { text-align: center; color: #94a3b8; padding: 2rem; font-style: italic; }
    .state-container { text-align: center; padding: 3rem; }
    .spinner {
      width: 32px;
      height: 32px;
      border: 3px solid #e2e8f0;
      border-top-color: #2563eb;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin: 0 auto 0.75rem;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
  `]
})
export class AttendanceCorrectionComponent implements OnInit {
  private correctionService = inject(AttendanceCorrectionService);
  private employeeService = inject(EmployeeService);

  records = signal<AttendanceRecordResponse[]>([]);
  isLoading = signal<boolean>(false);
  successMessage = signal<string>('');
  errorMessage = signal<string>('');

  searchQuery = '';
  fromDate = '';
  toDate = '';
  statusFilter = 'ALL';

  // Correction Modal State
  showCorrectionModal = signal<boolean>(false);
  selectedRecord: AttendanceRecordResponse | null = null;
  correctionAction: CorrectionActionType = 'CORRECT_CHECK_IN';
  inputCheckIn = '';
  inputCheckOut = '';
  correctionReason = '';
  isReviewStep = false;
  isSubmitting = false;
  modalError = '';

  // Audit History Modal State
  showAuditModal = signal<boolean>(false);
  auditLoading = false;
  auditLogs: AttendanceCorrectionResponse[] = [];

  // Manual Entry Modal State
  showManualModal = signal<boolean>(false);
  allEmployees: Employee[] = [];
  manualEmployeeId = '';
  manualWorkDate = '';
  manualCheckIn = '';
  manualCheckOut = '';
  manualReason = '';
  manualError = '';

  ngOnInit(): void {
    this.loadRecords();
    this.loadEmployees();
  }

  loadRecords(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');
    this.correctionService.getAttendanceRecords(
      this.searchQuery,
      undefined,
      this.fromDate || undefined,
      this.toDate || undefined,
      this.statusFilter
    ).subscribe({
      next: (page) => {
        this.records.set(page.content);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err?.error?.message || 'Failed to load attendance records.');
        this.isLoading.set(false);
      }
    });
  }

  loadEmployees(): void {
    this.employeeService.getEmployees().subscribe({
      next: (page) => {
        this.allEmployees = page.content;
      },
      error: (err) => console.error('Failed to preload employee list', err)
    });
  }

  onSearchChange(): void {
    this.loadRecords();
  }

  resetFilters(): void {
    this.searchQuery = '';
    this.fromDate = '';
    this.toDate = '';
    this.statusFilter = 'ALL';
    this.loadRecords();
  }

  openCorrectionModal(record: AttendanceRecordResponse): void {
    this.selectedRecord = record;
    this.correctionReason = '';
    this.isReviewStep = false;
    this.modalError = '';

    if (record.status === 'AUTO_CLOSED' || !record.checkOutTime) {
      this.correctionAction = 'RESOLVE_MISSED_CHECKOUT';
    } else {
      this.correctionAction = 'CORRECT_CHECK_IN';
    }

    this.inputCheckIn = record.checkInTime ? this.toLocalIso(record.checkInTime) : '';
    this.inputCheckOut = record.checkOutTime ? this.toLocalIso(record.checkOutTime) : '';
    this.showCorrectionModal.set(true);
  }

  closeCorrectionModal(): void {
    this.showCorrectionModal.set(false);
    this.selectedRecord = null;
  }

  onActionTypeChange(): void {
    this.isReviewStep = false;
    this.modalError = '';
  }

  proceedToReview(): void {
    this.modalError = '';
    if (!this.correctionReason || this.correctionReason.trim().length === 0) {
      this.modalError = 'Correction reason is mandatory.';
      return;
    }

    if (this.correctionAction !== 'VOID_RECORD') {
      if (this.inputCheckIn && this.inputCheckOut) {
        const inDate = new Date(this.inputCheckIn);
        const outDate = new Date(this.inputCheckOut);
        if (outDate.getTime() < inDate.getTime()) {
          this.modalError = 'Check-out time cannot precede check-in time.';
          return;
        }
      }
    }

    this.isReviewStep = true;
  }

  submitCorrection(): void {
    if (!this.selectedRecord) return;
    this.isSubmitting = true;
    this.modalError = '';

    const checkInIso = this.inputCheckIn ? new Date(this.inputCheckIn).toISOString() : undefined;
    const checkOutIso = this.inputCheckOut ? new Date(this.inputCheckOut).toISOString() : undefined;

    let targetStatus: 'CHECKED_IN' | 'CHECKED_OUT' | 'AUTO_CLOSED' | 'VOIDED' | undefined;
    if (this.correctionAction === 'VOID_RECORD') {
      targetStatus = 'VOIDED';
    } else if (checkOutIso) {
      targetStatus = 'CHECKED_OUT';
    } else {
      targetStatus = 'CHECKED_IN';
    }

    const request: AttendanceCorrectionRequest = {
      actionType: this.correctionAction,
      checkInTime: checkInIso,
      checkOutTime: checkOutIso,
      status: targetStatus,
      reason: this.correctionReason.trim(),
      recordVersion: this.selectedRecord.version
    };

    this.correctionService.correctAttendance(this.selectedRecord.id, request).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.closeCorrectionModal();
        this.successMessage.set('Attendance record successfully corrected and audit event recorded.');
        this.loadRecords();
        setTimeout(() => this.successMessage.set(''), 5000);
      },
      error: (err) => {
        this.isSubmitting = false;
        if (err.status === 409) {
          this.modalError = 'Concurrency conflict: This record was modified by another administrator. Please reload to see the latest values.';
        } else {
          this.modalError = err?.error?.message || 'Failed to submit attendance correction.';
        }
      }
    });
  }

  openAuditHistoryModal(record: AttendanceRecordResponse): void {
    this.selectedRecord = record;
    this.auditLoading = true;
    this.auditLogs = [];
    this.showAuditModal.set(true);

    this.correctionService.getRecordAuditHistory(record.id).subscribe({
      next: (page) => {
        this.auditLogs = page.content;
        this.auditLoading = false;
      },
      error: (err) => {
        this.auditLoading = false;
        this.errorMessage.set(err?.error?.message || 'Failed to load audit history.');
      }
    });
  }

  closeAuditModal(): void {
    this.showAuditModal.set(false);
  }

  loadGlobalAudit(): void {
    this.selectedRecord = null;
    this.auditLoading = true;
    this.auditLogs = [];
    this.showAuditModal.set(true);

    this.correctionService.searchAuditHistory(undefined, this.fromDate || undefined, this.toDate || undefined).subscribe({
      next: (page) => {
        this.auditLogs = page.content;
        this.auditLoading = false;
      },
      error: (err) => {
        this.auditLoading = false;
        this.errorMessage.set(err?.error?.message || 'Failed to load general audit history.');
      }
    });
  }

  openManualEntryModal(): void {
    this.manualEmployeeId = '';
    this.manualWorkDate = new Date().toISOString().split('T')[0];
    this.manualCheckIn = '';
    this.manualCheckOut = '';
    this.manualReason = '';
    this.manualError = '';
    this.showManualModal.set(true);
  }

  closeManualModal(): void {
    this.showManualModal.set(false);
  }

  submitManualEntry(): void {
    this.manualError = '';
    if (!this.manualEmployeeId) {
      this.manualError = 'Please select a staff member.';
      return;
    }
    if (!this.manualWorkDate) {
      this.manualError = 'Please select a work date.';
      return;
    }
    if (!this.manualCheckIn) {
      this.manualError = 'Please specify verified check-in time.';
      return;
    }
    if (!this.manualReason || this.manualReason.trim().length === 0) {
      this.manualError = 'Reason for manual entry is mandatory.';
      return;
    }

    if (this.manualCheckOut) {
      const inDate = new Date(this.manualCheckIn);
      const outDate = new Date(this.manualCheckOut);
      if (outDate.getTime() < inDate.getTime()) {
        this.manualError = 'Check-out time cannot precede check-in time.';
        return;
      }
    }

    this.isSubmitting = true;
    const request: ManualAttendanceRequest = {
      employeeId: this.manualEmployeeId,
      workDate: this.manualWorkDate,
      checkInTime: new Date(this.manualCheckIn).toISOString(),
      checkOutTime: this.manualCheckOut ? new Date(this.manualCheckOut).toISOString() : undefined,
      reason: this.manualReason.trim()
    };

    this.correctionService.createManualEntry(request).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.closeManualModal();
        this.successMessage.set('Manual attendance entry created successfully with audit log.');
        this.loadRecords();
        setTimeout(() => this.successMessage.set(''), 5000);
      },
      error: (err) => {
        this.isSubmitting = false;
        this.manualError = err?.error?.message || 'Failed to create manual attendance entry.';
      }
    });
  }

  formatTime(isoString?: string): string {
    if (!isoString) return '—';
    try {
      const date = new Date(isoString);
      return date.toLocaleTimeString('en-IN', {
        timeZone: 'Asia/Kolkata',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
      }) + ' IST';
    } catch {
      return isoString;
    }
  }

  private toLocalIso(isoString: string): string {
    try {
      const d = new Date(isoString);
      const offset = d.getTimezoneOffset() * 60000;
      const local = new Date(d.getTime() - offset);
      return local.toISOString().slice(0, 16);
    } catch {
      return '';
    }
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'CHECKED_IN': return 'status-checked-in';
      case 'CHECKED_OUT': return 'status-checked-out';
      case 'AUTO_CLOSED': return 'status-auto-closed';
      case 'VOIDED': return 'status-voided';
      default: return '';
    }
  }

  getIndicatorClass(indicator?: string): string {
    switch (indicator) {
      case 'Manual Entry': return 'indicator-manual';
      case 'Corrected': return 'indicator-corrected';
      default: return 'indicator-original';
    }
  }
}
