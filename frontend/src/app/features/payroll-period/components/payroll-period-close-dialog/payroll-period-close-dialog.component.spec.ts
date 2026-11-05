import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import {
  MAT_DIALOG_DATA,
  MatDialogRef,
} from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

import {
  PayrollPeriodCloseDialogComponent,
  PayrollPeriodCloseDialogData,
} from './payroll-period-close-dialog.component';
import { PayrollPeriodsService } from '../../services/payroll-periods.service';
import { PayrollPeriodError } from '../../services/payroll-period-error';
import {
  ClosePeriodResponse,
  PayrollPeriodDto,
} from '@features/payroll-period/models';
import {
  EmployeeWarning,
  MissingCurrencyRateWarning,
  ReportWarning,
} from '@features/reports/services/reports-error';

describe('PayrollPeriodCloseDialogComponent', () => {
  // ---------- fixtures ----------
  const DATA: PayrollPeriodCloseDialogData = {
    id: 'monthly-1',
    name: 'May 2026',
  };

  const MONTHLY: PayrollPeriodDto = {
    id: 'monthly-1',
    name: 'May 2026',
    startDate: '2026-05-01',
    endDate: '2026-05-31',
    frequency: 'MONTHLY',
    closed: false,
  };

  const BIWEEKLY: PayrollPeriodDto = {
    id: 'bw-1',
    name: 'BW1 May 2026',
    startDate: '2026-05-01',
    endDate: '2026-05-14',
    frequency: 'BI_WEEKLY',
    closed: false,
  };

  const MONTHLY_SETTLED: PayrollPeriodDto = { ...MONTHLY, closed: true };
  const BIWEEKLY_SETTLED: PayrollPeriodDto = { ...BIWEEKLY, closed: true };

  const WARN_EMPLOYEES: EmployeeWarning = {
    code: 'MISSING_PAYROLL_SUMMARY',
    severity: 'WARNING',
    message: 'Some employees with an in-effect assignment have no payroll summary',
    countConcerned: 3,
    employees: [
      { id: 'e1', matricule: 'EMP-1', name: 'A' },
      { id: 'e2', matricule: 'EMP-2', name: 'B' },
    ],
  };

  const WARN_FX: MissingCurrencyRateWarning = {
    code: 'MISSING_FX_RATE',
    severity: 'WARNING',
    message: 'Missing FX rate',
    currency: 'EUR',
  };

  function err(
    partial: Partial<PayrollPeriodError> & { code: string; message: string },
  ): PayrollPeriodError {
    return {
      code: partial.code,
      message: partial.message,
      errors: partial.errors ?? [],
      blockers: partial.blockers ?? [],
      warnings: partial.warnings ?? [],
      status: partial.status,
      timestamp: partial.timestamp,
    };
  }

  let fixture: ComponentFixture<PayrollPeriodCloseDialogComponent>;
  let component: PayrollPeriodCloseDialogComponent;
  let service: jasmine.SpyObj<PayrollPeriodsService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<PayrollPeriodCloseDialogComponent, boolean>>;

  beforeEach(async () => {
    service = jasmine.createSpyObj<PayrollPeriodsService>('PayrollPeriodsService', [
      'checkClose',
      'close',
    ]);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [PayrollPeriodCloseDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: PayrollPeriodsService, useValue: service },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: DATA },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PayrollPeriodCloseDialogComponent);
    component = fixture.componentInstance;
  });

  // ---------- helpers ----------
  function preflightOk(
    periods: PayrollPeriodDto[] = [MONTHLY, BIWEEKLY],
    warnings: ReportWarning[] = [],
  ): void {
    service.checkClose.and.returnValue(of({ periods, warnings }));
  }

  function preflightBlocked(
    blockers: ReportWarning[],
    warnings: ReportWarning[] = [],
  ): void {
    service.checkClose.and.returnValue(
      throwError(() => ({
        ...err({
          code: 'CLOSE_BLOCKED',
          message: 'The pay schedule cannot be settled because of data issues.',
          blockers,
          warnings,
        }),
        status: 409,
      })),
    );
  }

  function closeBlocked(
    blockers: ReportWarning[],
    warnings: ReportWarning[] = [],
  ): void {
    service.close.and.returnValue(
      throwError(() => ({
        ...err({
          code: 'CLOSE_BLOCKED',
          message: 'The pay schedule cannot be settled because of data issues.',
          blockers,
          warnings,
        }),
        status: 409,
      })),
    );
  }

  function mountAndDetect(): void {
    fixture.detectChanges();
  }

  // ==================================================================
  // Behaviour — initial checkClose
  // ==================================================================

  describe('ngOnInit → checkClose', () => {
    it('calls checkClose once on init and enters confirm on success', () => {
      preflightOk();
      mountAndDetect();

      expect(service.checkClose).toHaveBeenCalledOnceWith(DATA.id);
      expect(component.phase()).toBe('confirm');
      expect(component.periods()).toEqual([MONTHLY, BIWEEKLY]);
      expect(component.warnings()).toEqual([]);
      expect(component.blockers()).toEqual([]);
      expect(component.acceptClose()).toBeFalse();
    });

    it('puts the response warnings into the warnings signal on success', () => {
      preflightOk([MONTHLY], [WARN_EMPLOYEES, WARN_FX]);
      mountAndDetect();

      expect(component.warnings().length).toBe(2);
      expect(component.warnings()[0].code).toBe('MISSING_PAYROLL_SUMMARY');
      expect(component.warnings()[1].code).toBe('MISSING_FX_RATE');
    });

    it('enters blocked when checkClose returns 409 CLOSE_BLOCKED', () => {
      preflightBlocked([WARN_EMPLOYEES], [WARN_FX]);
      mountAndDetect();

      expect(component.phase()).toBe('blocked');
      expect(component.blockers().length).toBe(1);
      expect(component.warnings().length).toBe(1);
      expect(component.periods()).toEqual([]);
      expect(component.acceptClose()).toBeFalse();
    });

    it('enters error for a non-409 error', () => {
      service.checkClose.and.returnValue(
        throwError(() => err({ code: 'SERVER_DOWN', message: 'Down' })),
      );
      mountAndDetect();

      expect(component.phase()).toBe('error');
      expect(component.errorMessage()).toBe('Down');
    });
  });

  // ==================================================================
  // Behaviour — canSettle / primaryLabel
  // ==================================================================

  describe('canSettle / primaryLabel', () => {
    it('canSettle is false unless phase === "confirm" and acceptClose is true', () => {
      preflightOk();
      mountAndDetect();
      expect(component.canSettle()).toBeFalse();

      component.onAcceptChange(true);
      expect(component.canSettle()).toBeTrue();

      component.phase.set('settling');
      expect(component.canSettle()).toBeFalse();
    });

    it('primaryLabel is "Accept and settle" with warnings, "Settle payroll" without', () => {
      preflightOk([MONTHLY], []);
      mountAndDetect();
      expect(component.primaryLabel()).toBe('Settle payroll');
    });

    it('primaryLabel is "Accept and settle" when warnings are present', () => {
      preflightOk([MONTHLY], [WARN_FX]);
      mountAndDetect();
      expect(component.primaryLabel()).toBe('Accept and settle');
    });
  });

  // ==================================================================
  // Behaviour — onSettle
  // ==================================================================

  describe('onSettle', () => {
    it('is a no-op when canSettle is false', () => {
      preflightOk();
      mountAndDetect();

      component.onSettle();

      expect(service.close).not.toHaveBeenCalled();
      expect(component.phase()).toBe('confirm');
    });

    it('calls close when canSettle is true and enters done on success', () => {
      preflightOk();
      mountAndDetect();
      component.onAcceptChange(true);

      service.close.and.returnValue(
        of({
          periods: [MONTHLY_SETTLED, BIWEEKLY_SETTLED],
          warnings: [],
        }),
      );

      component.onSettle();

      expect(service.close).toHaveBeenCalledOnceWith(DATA.id);
      expect(component.phase()).toBe('done');
      expect(component.periods().every((p) => p.closed)).toBeTrue();
    });

    it('returns to blocked with the new blockers when close returns 409', () => {
      preflightOk();
      mountAndDetect();
      component.onAcceptChange(true);
      closeBlocked([WARN_EMPLOYEES]);

      component.onSettle();

      expect(component.phase()).toBe('blocked');
      expect(component.blockers().length).toBe(1);
      expect(component.acceptClose()).toBeFalse();
    });

    it('enters error for a non-CLOSE_BLOCKED error on close', () => {
      preflightOk();
      mountAndDetect();
      component.onAcceptChange(true);

      service.close.and.returnValue(
        throwError(() => err({ code: 'SERVER_DOWN', message: 'boom' })),
      );

      component.onSettle();

      expect(component.phase()).toBe('error');
      expect(component.errorMessage()).toBe('boom');
    });
  });

  // ==================================================================
  // Behaviour — onCancel (dialog return value)
  // ==================================================================

  describe('onCancel', () => {
    it('returns false from confirm / blocked / error', () => {
      preflightOk();
      mountAndDetect();

      component.onCancel();
      expect(dialogRef.close.calls.mostRecent()!.args[0]).toBe(false);
    });

    it('returns true from done', () => {
      preflightOk();
      mountAndDetect();
      component.phase.set('done');

      component.onCancel();
      expect(dialogRef.close.calls.mostRecent()!.args[0]).toBe(true);
    });

    it('is a no-op while settling', () => {
      preflightOk();
      mountAndDetect();
      component.phase.set('settling');

      component.onCancel();

      expect(dialogRef.close).not.toHaveBeenCalled();
    });
  });

  // ==================================================================
  // Behaviour — checkbox reset on every re-entry
  // ==================================================================

  describe('checkbox reset', () => {
    it('resets acceptClose when entering confirm from a retry preflight', () => {
      preflightBlocked([WARN_EMPLOYEES]);
      mountAndDetect();

      component.onAcceptChange(true);
      expect(component.acceptClose()).toBeTrue();

      preflightOk();
      component.onRetryPreflight();

      expect(component.phase()).toBe('confirm');
      expect(component.acceptClose()).toBeFalse();
    });

    it('resets acceptClose when settling returns 409 CLOSE_BLOCKED', () => {
      preflightOk();
      mountAndDetect();
      component.onAcceptChange(true);
      closeBlocked([WARN_EMPLOYEES]);

      component.onSettle();

      expect(component.acceptClose()).toBeFalse();
    });
  });

  // ==================================================================
  // Template — phase rendering
  // ==================================================================

  describe('template', () => {
    function byId(id: string): HTMLElement | null {
      return fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
    }

    it('blocked phase renders blockers with employee chips and truncation hint', () => {
      // 8 summaries reported, 6 employees in the fixture. The chip list is
      // capped at 5, and the truncation hint "… and more" is shown.
      const blockerWide: EmployeeWarning = {
        ...WARN_EMPLOYEES,
        countConcerned: 8,
        employees: [
          { id: 'e1', matricule: 'EMP-1', name: 'A' },
          { id: 'e2', matricule: 'EMP-2', name: 'B' },
          { id: 'e3', matricule: 'EMP-3', name: 'C' },
          { id: 'e4', matricule: 'EMP-4', name: 'D' },
          { id: 'e5', matricule: 'EMP-5', name: 'E' },
          { id: 'e6', matricule: 'EMP-6', name: 'F' },
        ],
      };
      preflightBlocked([blockerWide], []);
      mountAndDetect();

      expect(byId('close-dialog-blocked')).toBeTruthy();
      expect(byId('blocker-message-MISSING_PAYROLL_SUMMARY')!.textContent).toContain(
        'Some employees with an in-effect assignment have no payroll summary',
      );
      expect(byId('blocker-count-MISSING_PAYROLL_SUMMARY')!.textContent).toContain('8');

      // First 5 chips render; 6th is sliced off
      expect(byId('blocker-employee-e1')).toBeTruthy();
      expect(byId('blocker-employee-e5')).toBeTruthy();
      expect(byId('blocker-employee-e6')).toBeNull();

      // Truncation hint
      expect(byId('blocker-more-MISSING_PAYROLL_SUMMARY')!.textContent)
        .toContain('… and more');
    });

    it('confirm phase with warnings renders the checkbox and "Accept and settle" button', () => {
      preflightOk([MONTHLY, BIWEEKLY], [WARN_FX]);
      mountAndDetect();

      expect(byId('close-dialog-periods')).toBeTruthy();
      expect(byId('close-dialog-warnings')).toBeTruthy();
      expect(byId('close-dialog-accept')).toBeTruthy();

      const btn = byId('close-dialog-settle') as HTMLButtonElement;
      expect(btn.textContent).toContain('Accept and settle');
      expect(btn.disabled).toBeTrue();
    });

    it('confirm phase without warnings hides the warnings section and labels "Settle payroll"', () => {
      preflightOk([MONTHLY], []);
      mountAndDetect();

      expect(byId('close-dialog-warnings')).toBeNull();
      const btn = byId('close-dialog-settle') as HTMLButtonElement;
      expect(btn.textContent).toContain('Settle payroll');
    });

    it('confirm phase: checking the checkbox enables the settle button', () => {
      preflightOk([MONTHLY], []);
      mountAndDetect();

      expect((byId('close-dialog-settle') as HTMLButtonElement).disabled).toBeTrue();

      component.onAcceptChange(true);
      fixture.detectChanges();

      expect((byId('close-dialog-settle') as HTMLButtonElement).disabled).toBeFalse();
    });

    it('done phase shows the settled tree and only a Close button (no primary)', () => {
      preflightOk();
      mountAndDetect();
      component.onAcceptChange(true);
      service.close.and.returnValue(
        of({
          periods: [MONTHLY_SETTLED, BIWEEKLY_SETTLED],
          warnings: [],
        }),
      );
      component.onSettle();
      fixture.detectChanges();

      expect(byId('close-dialog-done')).toBeTruthy();
      expect(byId('close-dialog-settled-periods')).toBeTruthy();
      expect(byId('close-dialog-close')).toBeTruthy();
      expect(byId('close-dialog-settle')).toBeNull();
      expect(
        byId(`settled-period-${MONTHLY_SETTLED.id}`)!.getAttribute('data-closed'),
      ).toBe('true');
    });

    it('error phase shows the error banner and Retry button', () => {
      service.checkClose.and.returnValue(
        throwError(() => err({ code: 'SERVER_DOWN', message: 'boom' })),
      );
      mountAndDetect();

      expect(byId('close-dialog-error')!.textContent).toContain('boom');
      expect(byId('close-dialog-retry-preflight')).toBeTruthy();
    });
  });
});
