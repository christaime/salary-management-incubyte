import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'reports/aggregate' },
  {
    path: 'reports/aggregate',
    loadComponent: () =>
      import(
        './features/reports/pages/aggregate-report/aggregate-report.page'
      ).then((m) => m.AggregateReportPageComponent),
    title: 'Aggregate report',
  },
  {
    path: 'reports/earnings-ranking',
    loadComponent: () =>
      import('./features/reports/pages/earnings-ranking-report/earnings-ranking-report.page')
        .then(m => m.EarningsRankingReportPage),
    title: 'Earnings Ranking',
  },
  {
    path: 'employees',
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/employee/pages/employee-list/employee-list.page')
            .then(m => m.EmployeeListPage),
        title: 'Employees',
      },
      {
        path: 'new',
        loadComponent: () =>
          import('./features/employee/pages/employee-editor/employee-editor.page')
            .then(m => m.EmployeeEditorPage),
        title: 'New employee',
      },
      {
        path: ':id',
        loadComponent: () =>
          import('./features/employee/pages/employee-editor/employee-editor.page')
            .then(m => m.EmployeeEditorPage),
        title: 'Edit employee',
      },
    ],
  },
  {
    path: 'payroll-summaries',
    children: [
      {
        path: '',
        loadComponent: () => import('./features/payroll-summary/pages/payroll-summary-list/payroll-summary-list.page').then(m => m.PayrollSummaryListPage),
        title: 'Payroll Summaries',
      },
      {
         path: 'new',
         loadComponent: () => import('./features/payroll-summary/pages/payroll-summary-editor/payroll-summary-editor.page').then(m => m.PayrollSummaryEditorPage),
         title: 'New payroll summary',
      },
      {
         path: ':id/edit',
         loadComponent: () => import('./features/payroll-summary/pages/payroll-summary-editor/payroll-summary-editor.page').then(m => m.PayrollSummaryEditorPage),
         title: 'Edit payroll summary',
      },
      {
        path: ':id',
        loadComponent: () => import('./features/payroll-summary/pages/payroll-summary-detail/payroll-summary-detail.page').then(m => m.PayrollSummaryDetailPage),
        title: 'Payroll Summary',
      },
    ],
  },
  { path: '**', redirectTo: 'reports/aggregate' },
];
