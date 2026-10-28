import { EmploymentType } from './employment-type.model';

export interface EmployeeCriteria {
  keyword?: string;
  matricule?: string;
  anyOriginCountryIds?: string[] | null;
  anyAssignmentCountryIds?: string[] | null;
  anyDepartmentIds?: string[] | null;
  anyRoleIds?: string[] | null;
  anyEmploymentTypes?: EmploymentType[] | null;
  sortBy?: string;
  sortDirection?: 'ASC' | 'DESC';
  page: number;
  size: number;
}
