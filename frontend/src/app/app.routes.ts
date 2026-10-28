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
  { path: '**', redirectTo: 'reports/aggregate' },
];
