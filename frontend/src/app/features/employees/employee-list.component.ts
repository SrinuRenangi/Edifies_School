import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EmployeeService } from '../../core/services/employee.service';
import { Employee, CreateEmployeeRequest, UpdateEmployeeRequest } from '../../core/models/employee.model';

@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="employee-container">
      <header class="page-header">
        <div>
          <h2>Staff Directory</h2>
          <p class="subtitle">Manage staff profiles and attendance eligibility</p>
        </div>
        <button class="btn btn-primary" (click)="openRegisterModal()">
          + Register Employee
        </button>
      </header>

      <!-- Stats Grid -->
      <div class="stats-row">
        <div class="stat-card">
          <span class="stat-num">{{ totalCount() }}</span>
          <span class="stat-lbl">Total Staff</span>
        </div>
        <div class="stat-card">
          <span class="stat-num text-success">{{ activeCount() }}</span>
          <span class="stat-lbl">Active</span>
        </div>
        <div class="stat-card">
          <span class="stat-num text-muted">{{ inactiveCount() }}</span>
          <span class="stat-lbl">Inactive</span>
        </div>
      </div>

      <!-- Search & Filters -->
      <div class="toolbar">
        <input 
          type="text" 
          class="form-control search-input" 
          placeholder="Search by name, ID or department..." 
          [(ngModel)]="searchQuery" 
          (ngModelChange)="onSearchChange($event)"
        />
        <select class="form-control filter-select" [(ngModel)]="statusFilter" (ngModelChange)="applyFilter()">
          <option value="ALL">All Staff</option>
          <option value="ACTIVE">Active Only</option>
          <option value="INACTIVE">Inactive Only</option>
        </select>
      </div>

      <!-- Loading State -->
      <div *ngIf="isLoading()" class="state-container">
        <div class="spinner"></div>
        <p>Loading employee directory...</p>
      </div>

      <!-- Error State -->
      <div *ngIf="errorMessage()" class="alert alert-danger">
        {{ errorMessage() }}
        <button class="btn btn-secondary btn-sm" (click)="loadEmployees()">Retry</button>
      </div>

      <!-- Employee Table -->
      <div *ngIf="!isLoading() && !errorMessage()" class="table-card">
        <table class="table">
          <thead>
            <tr>
              <th>Employee ID</th>
              <th>Full Name</th>
              <th>Department</th>
              <th>Status</th>
              <th>Registered At</th>
              <th class="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let emp of displayedEmployees()">
              <td><span class="badge-mono">{{ emp.employeeId }}</span></td>
              <td><strong>{{ emp.fullName }}</strong></td>
              <td>{{ emp.department }}</td>
              <td>
                <span class="status-pill" [class.active]="emp.status === 'ACTIVE'">
                  {{ emp.status }}
                </span>
              </td>
              <td>{{ emp.createdAt | date:'medium':'Asia/Kolkata' }}</td>
              <td class="text-right">
                <button class="btn btn-sm btn-outline" (click)="openEditModal(emp)">Edit</button>
                <button 
                  class="btn btn-sm" 
                  [class.btn-danger]="emp.status === 'ACTIVE'"
                  [class.btn-success]="emp.status === 'INACTIVE'"
                  (click)="toggleStatus(emp)">
                  {{ emp.status === 'ACTIVE' ? 'Deactivate' : 'Activate' }}
                </button>
              </td>
            </tr>
            <tr *ngIf="displayedEmployees().length === 0">
              <td colspan="6" class="text-center empty-cell">
                No employees matching current filter.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .employee-container { max-width: 1200px; margin: 0 auto; padding: 1.5rem; font-family: sans-serif; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
    .subtitle { color: #64748b; font-size: 0.9rem; }
    .stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; margin-bottom: 1.5rem; }
    .stat-card { background: #fff; padding: 1rem; border-radius: 8px; border: 1px solid #e2e8f0; text-align: center; }
    .stat-num { font-size: 1.5rem; font-weight: bold; display: block; }
    .text-success { color: #10b981; }
    .text-muted { color: #94a3b8; }
    .toolbar { display: flex; gap: 1rem; margin-bottom: 1rem; }
    .search-input { flex: 1; padding: 0.5rem; border: 1px solid #cbd5e1; border-radius: 6px; }
    .filter-select { width: 160px; padding: 0.5rem; border: 1px solid #cbd5e1; border-radius: 6px; }
    .table-card { background: #fff; border-radius: 8px; border: 1px solid #e2e8f0; overflow: hidden; }
    .table { width: 100%; border-collapse: collapse; }
    .table th, .table td { padding: 0.75rem 1rem; border-bottom: 1px solid #f1f5f9; text-align: left; }
    .badge-mono { font-family: monospace; background: #f1f5f9; padding: 2px 6px; border-radius: 4px; }
    .status-pill { padding: 2px 8px; border-radius: 12px; font-size: 0.75rem; background: #e2e8f0; color: #475569; }
    .status-pill.active { background: #ecfdf5; color: #065f46; }
    .text-right { text-align: right; }
    .btn { padding: 0.45rem 0.9rem; border-radius: 6px; cursor: pointer; border: none; font-weight: 500; }
    .btn-primary { background: #4f46e5; color: #fff; }
    .btn-sm { padding: 0.25rem 0.5rem; font-size: 0.8rem; margin-left: 0.35rem; }
    .btn-outline { background: transparent; border: 1px solid #cbd5e1; }
    .btn-danger { background: #ef4444; color: #fff; }
    .btn-success { background: #10b981; color: #fff; }
    .state-container { text-align: center; padding: 3rem; color: #64748b; }
    .empty-cell { padding: 2rem; color: #94a3b8; }
  `]
})
export class EmployeeListComponent implements OnInit {
  private readonly employeeService = inject(EmployeeService);

  employees = signal<Employee[]>([]);
  displayedEmployees = signal<Employee[]>([]);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  searchQuery = '';
  statusFilter = 'ALL';

  totalCount = signal<number>(0);
  activeCount = signal<number>(0);
  inactiveCount = signal<number>(0);

  ngOnInit(): void {
    this.loadEmployees();
  }

  loadEmployees(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.employeeService.getAllEmployees(this.searchQuery).subscribe({
      next: (data) => {
        this.employees.set(data);
        this.updateStats(data);
        this.applyFilter();
        this.isLoading.set(false);
      },
      error: (err) => {
        this.errorMessage.set('Could not load employees from server.');
        this.isLoading.set(false);
      }
    });
  }

  onSearchChange(query: string): void {
    this.searchQuery = query;
    this.loadEmployees();
  }

  applyFilter(): void {
    let list = this.employees();
    if (this.statusFilter !== 'ALL') {
      list = list.filter(e => e.status === this.statusFilter);
    }
    this.displayedEmployees.set(list);
  }

  updateStats(list: Employee[]): void {
    this.totalCount.set(list.length);
    this.activeCount.set(list.filter(e => e.status === 'ACTIVE').length);
    this.inactiveCount.set(list.filter(e => e.status === 'INACTIVE').length);
  }

  toggleStatus(emp: Employee): void {
    const nextStatus = emp.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    if (!confirm(`Are you sure you want to change ${emp.fullName}'s status to ${nextStatus}?`)) {
      return;
    }
    this.employeeService.updateStatus(emp.id, { status: nextStatus }).subscribe({
      next: () => this.loadEmployees(),
      error: () => alert('Failed to update status.')
    });
  }

  openRegisterModal(): void {
    const id = prompt('Enter Employee ID (e.g. EMP-101):');
    if (!id) return;
    const name = prompt('Enter Full Name:');
    if (!name) return;
    const dept = prompt('Enter Department:', 'General') || 'General';

    this.employeeService.createEmployee({ employeeId: id, fullName: name, department: dept }).subscribe({
      next: () => this.loadEmployees(),
      error: (err) => alert(err.error?.message || 'Failed to create employee.')
    });
  }

  openEditModal(emp: Employee): void {
    const name = prompt('Update Full Name:', emp.fullName);
    if (!name) return;
    const dept = prompt('Update Department:', emp.department) || emp.department;

    this.employeeService.updateEmployee(emp.id, { fullName: name, department: dept }).subscribe({
      next: () => this.loadEmployees(),
      error: (err) => alert(err.error?.message || 'Failed to update employee.')
    });
  }
}
