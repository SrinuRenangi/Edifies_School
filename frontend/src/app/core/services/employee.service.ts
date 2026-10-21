import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Employee, CreateEmployeeRequest, UpdateEmployeeRequest, UpdateStatusRequest } from '../models/employee.model';

@Injectable({
  providedIn: 'root'
})
export class EmployeeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/admin/employees';

  getAllEmployees(search?: string): Observable<Employee[]> {
    let params = new HttpParams();
    if (search && search.trim()) {
      params = params.set('search', search.trim());
    }
    return this.http.get<Employee[]>(this.baseUrl, { params });
  }

  getEmployeeById(id: string): Observable<Employee> {
    return this.http.get<Employee>(`${this.baseUrl}/${id}`);
  }

  createEmployee(request: CreateEmployeeRequest): Observable<Employee> {
    return this.http.post<Employee>(this.baseUrl, request);
  }

  updateEmployee(id: string, request: UpdateEmployeeRequest): Observable<Employee> {
    return this.http.patch<Employee>(`${this.baseUrl}/${id}`, request);
  }

  updateStatus(id: string, request: UpdateStatusRequest): Observable<Employee> {
    return this.http.patch<Employee>(`${this.baseUrl}/${id}/status`, request);
  }
}
