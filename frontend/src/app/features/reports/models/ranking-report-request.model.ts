import { EmploymentType } from './employment-type.model';
import { GroupKey } from './group-key.model';

export interface EarningRankingReportRequest {
  periodId: string;
  groupKey: GroupKey | null;
  countryId: string | null;
  departmentId: string | null;
  roleId: string | null;
  employmentType: EmploymentType | null;
}
