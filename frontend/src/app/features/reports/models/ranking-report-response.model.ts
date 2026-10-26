import { EmploymentType } from './employment-type.model';
import { GroupKey } from './group-key.model';
import { PayrollPeriodDto } from '@features/payroll-period/models';
import { PositionContextDto } from './position-context.model';
import { EmployeeRefDto } from './employee.model';
import { RefDto } from '@core/models';
import { ReportWarningDto } from './report-warning.model';

export interface EarnerRankingDto {
  monthlyReferenceAmount: number;
  frequency: 'MONTHLY' | 'BI_WEEKLY';
  percentile: number | null;     // 9966 = P99.66, 4 = P0.04, null if n < 10
  rank: number;
  employee: EmployeeRefDto;
  role: RefDto | null;
  department: RefDto | null;
  country: RefDto | null;
  employmentType: EmploymentType | null;
  period: PayrollPeriodDto;
}

export interface EarningRankingReportResponse {
  kind: 'RANKING';
  period: PayrollPeriodDto;
  referenceCurrency: string;
  groupKey: GroupKey | null;
  tops: EarnerRankingDto[];
  bottoms: EarnerRankingDto[];
  positionContext: PositionContextDto | null;
  totalReferenceAmount: number;
  totalDimensionReferenceAmount: number;
  totalMonthlyPeriod: number;
  totalBiWeeklyPeriod: number;
  warnings?: ReportWarningDto[];
}
