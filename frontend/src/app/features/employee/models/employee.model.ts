import { EmployeeAssignmentDto, EmployeeAssignmentSaveRequest, EmployeeAssignmentToCloseRequest} from './employee-assignment.model';
import { RefDto } from '@core/models/reference-data.model';

export interface EmployeeRefDto {
  id: string;
  matricule: string;
  name: string;
}

export interface EmployeeWithInEffectAssignmentDto {
  id: string;
  matricule: string;
  name: string;
  email: string;
  birthDate: string | null;
  originCountry: RefDto | null;
  assignmentInEffect: EmployeeAssignmentDto | null;
}

export interface EmployeeSaveRequest {
  id: string | null;             // null on create
  matricule: string;
  name: string;
  email: string;
  birthDate: string | null;
  originCountryId: string | null;
  assignmentInEffect: EmployeeAssignmentSaveRequest | null;
  toClose: EmployeeAssignmentToCloseRequest | null;
}
