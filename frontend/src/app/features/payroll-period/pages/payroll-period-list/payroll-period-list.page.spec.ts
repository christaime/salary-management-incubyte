import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';

import { PayrollPeriodListPage } from './payroll-period-list.page';
import { PayrollPeriodsService } from '../../services/payroll-periods.service';
import { PayrollPeriodError } from '../../services/payroll-period-error';
import { PayrollPeriodFilterValue } from '../../components/payroll-period-filter-bar/payroll-period-filter-bar.component';
import {
  PayrollPeriodCloseDialogComponent,
  PayrollPeriodCloseDialogData,
} from '../../components/payroll-period-close-dialog/payroll-period-close-dialog.component';
import {
  PayrollPeriodCriteria,
  PayrollPeriodDto,
} from '@features/payroll-period/models';
import { DataPage } from '@core/models';
import { ConfirmDialogComponent } from '@core/components/confirm-dialog/confirm-dialog.component';

describe('PayrollPeriodListPage', () => {
  // ---------- fixtures ----------
  const MONTHLY: PayrollPeriodDto = {
    id: 'm-1',
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

  const DEFAULT_FILTERS: PayrollPeriodFilterValue = {
    keyword: null,
    frequency: null,
    isClosed: null,
    startDate: null,
    endDate: null,
  };

  function page(
    rows: PayrollPeriodDto[],
    total = rows.length,
  ): DataPage<PayrollPeriodDto> {
    return {
      content: rows,
      page: 0,
      size: 20,
      totalElements: total,
      totalPages: Math.max(1, Math.ceil(total / 20)),
      first: true,
      last: true,
      empty: rows.length === 0,
    };
  }

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

  let fixture: ComponentFixture<PayrollPeriodListPage>;
  let component: PayrollPeriodListPage;
  let service: jasmine.SpyObj<PayrollPeriodsService>;
  let router: jasmine.SpyObj<Router>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  beforeEach(async () => {
    service = jasmine.createSpyObj<PayrollPeriodsService>('PayrollPeriodsService', [
      'search',
      'delete',
    ]);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    snackBar = jasmine.createSpyObj<MatSnackBar>('MatSnackBar', ['open']);

    await TestBed.configureTestingModule({
      imports: [PayrollPeriodListPage, NoopAnimationsModule],
      providers: [
        { provide: PayrollPeriodsService, useValue: service },
        { provide: Router, useValue: router },
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PayrollPeriodListPage);
    component = fixture.componentInstance;
  });

  // ---------- helpers ----------
  function lastCriteria(): PayrollPeriodCriteria {
    const call = service.search.calls.mostRecent();
    if (!call) throw new Error('search was not called');
    return call.args[0] as PayrollPeriodCriteria;
  }

  function byId(id: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  }

  // ==================================================================
  // Behaviour — initial load
  // ==================================================================

  describe('initial load', () => {
    it('calls search with default criteria on ngOnInit', () => {
      service.search.and.returnValue(of(page([MONTHLY, BIWEEKLY])));

      fixture.detectChanges();

      expect(service.search).toHaveBeenCalledTimes(1);
      const criteria = lastCriteria();
      expect(criteria).toEqual({
        keyword: undefined,
        startDate: undefined,
        endDate: undefined,
        frequency: undefined,
        isClosed: undefined,
        sortBy: 'startDate',
        sortDirection: 'DESC',
        page: 0,
        size: 20,
      } as PayrollPeriodCriteria);
    });

    it('populates rows and totalElements on success and enters success', () => {
      service.search.and.returnValue(of(page([MONTHLY, BIWEEKLY], 2)));
      fixture.detectChanges();

      expect(component.rows()).toEqual([MONTHLY, BIWEEKLY]);
      expect(component.totalElements()).toBe(2);
      expect(component.state()).toBe('success');
      expect(component.error()).toBeNull();
    });

    it('enters error and clears rows when the initial search fails', () => {
      service.search.and.returnValue(
        throwError(() => err({ code: 'BOOM', message: 'boom' })),
      );
      fixture.detectChanges();

      expect(component.state()).toBe('error');
      expect(component.error()).toBe('boom');
      expect(component.rows()).toEqual([]);
      expect(component.totalElements()).toBe(0);
    });
  });

  // ==================================================================
  // Behaviour — filter / pagination
  // ==================================================================

  describe('filter changes', () => {
    beforeEach(() => {
      service.search.and.returnValue(of(page([MONTHLY])));
      fixture.detectChanges();
      service.search.calls.reset();
    });

    it('onFilterChange stores filters, resets page to 0, and reloads', () => {
      component.page.set(3);

      const next: PayrollPeriodFilterValue = {
        keyword: 'May',
        frequency: 'MONTHLY',
        isClosed: false,
        startDate: '2026-05-01',
        endDate: '2026-05-31',
      };
      component.onFilterChange(next);

      expect(component.filters()).toEqual(next);
      expect(component.page()).toBe(0);
      expect(service.search).toHaveBeenCalledTimes(1);
      const c = lastCriteria();
      expect(c.page).toBe(0);
      expect(c.keyword).toBe('May');
      expect(c.isClosed).toBeFalse();
    });

    it('onFilterReset restores defaults and reloads with no filters', () => {
      component.filters.set({
        keyword: 'May',
        frequency: 'MONTHLY',
        isClosed: true,
        startDate: '2026-05-01',
        endDate: '2026-05-31',
      });
      component.page.set(2);

      component.onFilterReset();

      expect(component.filters()).toEqual(DEFAULT_FILTERS);
      expect(component.page()).toBe(0);
      expect(service.search).toHaveBeenCalledTimes(1);
      const c = lastCriteria();
      expect(c.keyword).toBeUndefined();
      expect(c.frequency).toBeUndefined();
      expect(c.isClosed).toBeUndefined();
    });

    it('onPageChange preserves filters and sends the new page/size', () => {
      component.filters.set({ ...DEFAULT_FILTERS, keyword: 'May' });

      component.onPageChange({ page: 2, size: 50 });

      expect(component.page()).toBe(2);
      expect(component.size()).toBe(50);
      const c = lastCriteria();
      expect(c.page).toBe(2);
      expect(c.size).toBe(50);
      expect(c.keyword).toBe('May');
    });
  });

  // ==================================================================
  // Behaviour — navigation
  // ==================================================================

  describe('navigation', () => {
    beforeEach(() => {
      service.search.and.returnValue(of(page([])));
      fixture.detectChanges();
    });

    it('onNew navigates to /payroll-periods/new', () => {
      component.onNew();
      expect(router.navigate).toHaveBeenCalledOnceWith(['/payroll-periods/new']);
    });

    it('onEdit navigates to /:id/edit', () => {
      component.onEdit(MONTHLY);
      expect(router.navigate).toHaveBeenCalledOnceWith([
        '/payroll-periods',
        MONTHLY.id,
        'edit',
      ]);
    });
  });

  // ==================================================================
  // Behaviour — delete
  // ==================================================================

  describe('delete', () => {
    function confirmDialogReturns(value: boolean): void {
      dialog.open.and.returnValue({
        afterClosed: () => of(value),
      } as any);
    }

    beforeEach(() => {
      service.search.and.returnValue(of(page([MONTHLY])));
      fixture.detectChanges();
      service.search.calls.reset();
    });

    it('opens the confirm dialog with the expected shape', () => {
      confirmDialogReturns(false);
      component.onDelete(MONTHLY);

      const [dialogComponent, config] = dialog.open.calls.mostRecent()!.args;
      expect(dialogComponent).toBe(ConfirmDialogComponent);
      const cfg = config as {
        data: { title: string; message: string; danger: boolean };
      };
      expect(cfg.data.title).toBe('Delete pay schedule');
      expect(cfg.data.message).toContain('May 2026');
      expect(cfg.data.danger).toBeTrue();
    });

    it('on confirm: calls delete, shows a snackbar, and reloads', () => {
      confirmDialogReturns(true);
      service.delete.and.returnValue(of(void 0));

      component.onDelete(MONTHLY);

      expect(service.delete).toHaveBeenCalledOnceWith(MONTHLY.id);
      expect(snackBar.open).toHaveBeenCalledWith(
        'Pay schedule deleted',
        'Dismiss',
        jasmine.any(Object),
      );
      expect(service.search).toHaveBeenCalledTimes(1);
    });

    it('on cancel: does nothing', () => {
      confirmDialogReturns(false);
      component.onDelete(MONTHLY);

      expect(service.delete).not.toHaveBeenCalled();
      expect(service.search).not.toHaveBeenCalled();
      expect(snackBar.open).not.toHaveBeenCalled();
    });

    it('on PERIOD_REFERENCED error: sets the HR message and enters error state', () => {
      confirmDialogReturns(true);
      service.delete.and.returnValue(
        throwError(() =>
          err({ code: 'PERIOD_REFERENCED', message: 'backend message' }),
        ),
      );

      component.onDelete(MONTHLY);

      expect(component.state()).toBe('error');
      expect(component.error()).toBe(
        'This pay schedule is used by payroll records and cannot be deleted.',
      );
      expect(service.search).not.toHaveBeenCalled();
    });

    it('on PERIOD_CLOSED error: sets the settled message', () => {
      confirmDialogReturns(true);
      service.delete.and.returnValue(
        throwError(() => err({ code: 'PERIOD_CLOSED', message: 'backend message' })),
      );

      component.onDelete(MONTHLY);

      expect(component.error()).toBe(
        'This pay schedule is already settled and cannot be deleted.',
      );
    });
  });

  // ==================================================================
  // Behaviour — settle
  // ==================================================================

  describe('settle', () => {
    beforeEach(() => {
      service.search.and.returnValue(of(page([MONTHLY])));
      fixture.detectChanges();
      service.search.calls.reset();
    });

    it('opens the close dialog with the id/name and the widened panel config', () => {
      dialog.open.and.returnValue({ afterClosed: () => of(false) } as any);

      component.onSettle(MONTHLY);

      const [dialogComponent, config] = dialog.open.calls.mostRecent()!.args;
      expect(dialogComponent).toBe(PayrollPeriodCloseDialogComponent);
      const typed = config as {
        data: PayrollPeriodCloseDialogData;
        disableClose: boolean;
        width: string;
        maxWidth: string;
      };
      expect(typed.data).toEqual({ id: MONTHLY.id, name: MONTHLY.name });
      expect(typed.disableClose).toBeTrue();
      expect(typed.width).toBe('900px');
      expect(typed.maxWidth).toBe('95vw');
    });

    it('on true: shows the success snackbar and reloads', () => {
      dialog.open.and.returnValue({ afterClosed: () => of(true) } as any);

      component.onSettle(MONTHLY);

      expect(snackBar.open).toHaveBeenCalledWith(
        'Payroll settled',
        'Dismiss',
        jasmine.any(Object),
      );
      expect(service.search).toHaveBeenCalledTimes(1);
    });

    it('on false: does not reload and does not show a snackbar', () => {
      dialog.open.and.returnValue({ afterClosed: () => of(false) } as any);

      component.onSettle(MONTHLY);

      expect(snackBar.open).not.toHaveBeenCalled();
      expect(service.search).not.toHaveBeenCalled();
    });
  });

  // ==================================================================
  // Template
  // ==================================================================

  describe('template', () => {
    it('renders the error banner under the filter bar when state = error', () => {
      service.search.and.returnValue(
        throwError(() => err({ code: 'BOOM', message: 'server unreachable' })),
      );
      fixture.detectChanges();

      expect(byId('payroll-period-list-error')).toBeTruthy();
      expect(byId('payroll-period-list-error')!.textContent).toContain(
        'server unreachable',
      );
    });

    it('paginator renders only when state = success and totalElements > 0', () => {
      service.search.and.returnValue(of(page([MONTHLY], 1)));
      fixture.detectChanges();

      expect(byId('paginator')).toBeTruthy();
    });

    it('paginator is hidden when success but totalElements = 0', () => {
      service.search.and.returnValue(of(page([], 0)));
      fixture.detectChanges();

      expect(byId('paginator')).toBeNull();
    });

    it('table is rendered even on error (with the error banner above it)', () => {
      service.search.and.returnValue(
        throwError(() => err({ code: 'BOOM', message: 'x' })),
      );
      fixture.detectChanges();

      expect(byId('payroll-period-table')).toBeTruthy();
      expect(byId('payroll-period-list-error')).toBeTruthy();
    });
  });
});
