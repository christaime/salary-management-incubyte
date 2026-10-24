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
  { path: '**', redirectTo: 'reports/aggregate' },
];
