export type EmployeeStatus = 'ACTIVE' | 'INACTIVE';

export interface Employee {
  id: string;
  employeeId: string;
  fullName: string;
  department: string;
  status: EmployeeStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEmployeeRequest {
  employeeId: string;
  fullName: string;
  department?: string;
}

export interface UpdateEmployeeRequest {
  fullName: string;
  department?: string;
}

export interface UpdateStatusRequest {
  status: EmployeeStatus;
}
