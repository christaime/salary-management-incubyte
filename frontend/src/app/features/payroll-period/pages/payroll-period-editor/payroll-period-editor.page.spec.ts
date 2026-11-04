import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  ParamMap,
  Router,
  convertToParamMap,
} from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { PayrollPeriodEditorPage } from './payroll-period-editor.page';
import { PayrollPeriodsService } from '../../services/payroll-periods.service';
import { PayrollPeriodError } from '../../services/payroll-period-error';
import {
  PayrollPeriodCreateRequest,
  PayrollPeriodDto,
  SubPayrollPeriodCreateResponse,
} from '@features/payroll-period/models';

describe('PayrollPeriodEditorPage', () => {
  // ---------- fixtures ----------
  const MONTHLY: PayrollPeriodDto = {
    id: 'm-1',
    name: 'May 2026',
    startDate: '2026-05-01',
    endDate: '2026-05-31',
    frequency: 'MONTHLY',
    closed: false,
    referenced: false,
  };

  const MONTHLY_REFERENCED: PayrollPeriodDto = {
    ...MONTHLY,
    referenced: true,
  };

  const SUB_RESPONSE: SubPayrollPeriodCreateResponse = {
    exist: [
      {
        id: 'bw-old',
        name: 'BW1 Apr 2026',
        startDate: '2026-04-01',
        endDate: '2026-04-14',
        frequency: 'BI_WEEKLY',
        closed: false,
      },
    ],
    newPeriods: [
      {
        name: 'BW1 May 2026',
        startDate: '2026-05-01',
        endDate: '2026-05-14',
        frequency: 'BI_WEEKLY',
      },
      {
        name: 'BW2 May 2026',
        startDate: '2026-05-15',
        endDate: '2026-05-28',
        frequency: 'BI_WEEKLY',
      },
    ],
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

  let fixture: ComponentFixture<PayrollPeriodEditorPage>;
  let component: PayrollPeriodEditorPage;
  let service: jasmine.SpyObj<PayrollPeriodsService>;
  let router: jasmine.SpyObj<Router>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  let paramMap$: BehaviorSubject<ParamMap>;
  let route: ActivatedRoute;

  function makeRouteWithId(id: string | null): void {
    const map = convertToParamMap(id ? { id } : {});
    paramMap$ = new BehaviorSubject(map);
    route = {
      get paramMap() {
        return paramMap$.asObservable();
      },
      get snapshot() {
        return { paramMap: paramMap$.value };
      },
    } as unknown as ActivatedRoute;
  }

  async function build(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PayrollPeriodEditorPage, NoopAnimationsModule],
      providers: [
        { provide: PayrollPeriodsService, useValue: service },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: route },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PayrollPeriodEditorPage);
    component = fixture.componentInstance;
  }

  beforeEach(() => {
    service = jasmine.createSpyObj<PayrollPeriodsService>('PayrollPeriodsService', [
      'create',
      'update',
      'createBatch',
      'generateSubPeriods',
      'getById',
    ]);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    snackBar = jasmine.createSpyObj<MatSnackBar>('MatSnackBar', ['open']);
    makeRouteWithId(null);
  });

  // ==================================================================
  // Behaviour — ngOnInit
  // ==================================================================

  describe('ngOnInit — new mode', () => {
    beforeEach(async () => {
      await build();
      fixture.detectChanges();
    });

    it('sets mode to "new" and patches a next-month start + suggested end', () => {
      expect(component.mode()).toBe('new');
      expect(component.state()).toBe('ready');
      expect(component.form.controls.frequency.value).toBe('MONTHLY');

      const start = component.form.controls.startDate.value;
      const end = component.form.controls.endDate.value;
      expect(start.endsWith('-01')).toBeTrue();
      expect(end > start).toBeTrue();
      expect(component.form.controls.endDate.valid).toBeTrue();
    });

    it('does not call any service method on mount', () => {
      expect(service.getById).not.toHaveBeenCalled();
      expect(service.create).not.toHaveBeenCalled();
      expect(service.update).not.toHaveBeenCalled();
      expect(service.createBatch).not.toHaveBeenCalled();
      expect(service.generateSubPeriods).not.toHaveBeenCalled();
    });
  });

  describe('ngOnInit — edit mode', () => {
    it('calls getById and preloads the form on success', async () => {
      makeRouteWithId(MONTHLY.id);
      service.getById.and.returnValue(of(MONTHLY));
      await build();
      fixture.detectChanges();

      expect(service.getById).toHaveBeenCalledOnceWith(MONTHLY.id);
      expect(component.mode()).toBe('edit');
      expect(component.periodId()).toBe(MONTHLY.id);
      expect(component.state()).toBe('ready');
      expect(component.form.controls.name.value).toBe(MONTHLY.name);
      expect(component.form.controls.startDate.value).toBe(MONTHLY.startDate);
      expect(component.form.controls.endDate.value).toBe(MONTHLY.endDate);
      expect(component.nameOnly()).toBeFalse();
      expect(component.nameOnlyNotice()).toBeNull();
    });

    it('locks the form at load when referenced = true', async () => {
      makeRouteWithId(MONTHLY_REFERENCED.id);
      service.getById.and.returnValue(of(MONTHLY_REFERENCED));
      await build();
      fixture.detectChanges();

      expect(component.nameOnly()).toBeTrue();
      expect(component.form.controls.frequency.disabled).toBeTrue();
      expect(component.form.controls.startDate.disabled).toBeTrue();
      expect(component.form.controls.endDate.disabled).toBeTrue();
      expect(component.form.controls.name.enabled).toBeTrue();
      expect(component.nameOnlyNotice()).toContain('only its name can be changed');
      expect(component.nameOnlyNoticeDismissed()).toBeFalse();
      expect(component.formError()).toBeNull();
    });

    it('dismisses the name-only notice without unlocking the form', async () => {
      makeRouteWithId(MONTHLY_REFERENCED.id);
      service.getById.and.returnValue(of(MONTHLY_REFERENCED));
      await build();
      fixture.detectChanges();

      component.onDismissNameOnlyNotice();
      fixture.detectChanges();

      expect(component.nameOnlyNoticeDismissed()).toBeTrue();
      expect(component.nameOnly()).toBeTrue();
      expect(component.form.controls.startDate.disabled).toBeTrue();
    });

    it('enters not-found when the load returns NOT_FOUND', async () => {
      makeRouteWithId('missing');
      service.getById.and.returnValue(
        throwError(() => err({ code: 'NOT_FOUND', message: 'gone' })),
      );
      await build();
      fixture.detectChanges();

      expect(component.state()).toBe('not-found');
      expect(component.formError()).toBeNull();
    });

    it('enters error when the load fails for a non-NOT_FOUND reason', async () => {
      makeRouteWithId(MONTHLY.id);
      service.getById.and.returnValue(
        throwError(() =>
          err({ code: 'NETWORK_OR_SERVER_ERROR', message: 'server unreachable' }),
        ),
      );
      await build();
      fixture.detectChanges();

      expect(component.state()).toBe('error');
      expect(component.formError()).toBe('server unreachable');
    });
  });

  // ==================================================================
  // Behaviour — end date suggestion
  // ==================================================================

  describe('recomputeEndDate', () => {
    beforeEach(async () => {
      await build();
      fixture.detectChanges();
    });

    it('suggests endDate = last of month when user picks the 1st (MONTHLY)', () => {
      component.form.controls.startDate.setValue('2026-05-01');
      expect(component.form.controls.endDate.value).toBe('2026-05-31');
    });

    it('suggests start + 27 when user picks a mid-month start', () => {
      component.form.controls.startDate.setValue('2026-05-10');
      expect(component.form.controls.endDate.value).toBe('2026-06-06');
    });

    it('re-suggests endDate after the user edits it and then changes start', () => {
      component.form.controls.startDate.setValue('2026-05-01');
      expect(component.form.controls.endDate.value).toBe('2026-05-31');

      component.form.controls.endDate.setValue('2026-05-28');
      expect(component.form.controls.endDate.value).toBe('2026-05-28');

      component.form.controls.startDate.setValue('2026-06-01');
      expect(component.form.controls.endDate.value).toBe('2026-06-30');
    });

    it('always forces endDate = start + 13 for BI_WEEKLY', () => {
      component.form.controls.frequency.setValue('BI_WEEKLY');
      component.form.controls.startDate.setValue('2026-05-01');
      expect(component.form.controls.endDate.value).toBe('2026-05-14');

      component.form.controls.startDate.setValue('2026-05-03');
      expect(component.form.controls.endDate.value).toBe('2026-05-16');
    });

    it('marks endDate as invalid when the span is out of range', () => {
      component.form.controls.startDate.setValue('2026-05-01');
      component.form.controls.endDate.setValue('2026-05-20');
      expect(component.form.controls.endDate.hasError('monthlySpan')).toBeTrue();
    });
  });

  // ==================================================================
  // Behaviour — cascade invalidation
  // ==================================================================

  describe('cascade invalidation', () => {
    beforeEach(async () => {
      service.generateSubPeriods.and.returnValue(of(SUB_RESPONSE));
      await build();
      fixture.detectChanges();
    });

    it('does not fetch on init unless the user checks the box', () => {
      expect(service.generateSubPeriods).not.toHaveBeenCalled();
    });

    it('fetch fires on explicit check', () => {
      component.form.controls.startDate.setValue('2026-05-01');
      component.form.controls.cascadeToBiWeekly.setValue(true);

      expect(service.generateSubPeriods).toHaveBeenCalledOnceWith(
        '2026-05-01',
        '2026-05-31',
      );
    });

    it('populates previewRows and subPeriodNames on success', () => {
      component.form.controls.startDate.setValue('2026-05-01');
      component.form.controls.cascadeToBiWeekly.setValue(true);

      expect(component.previewRows().length).toBe(3);
      expect(component.previewRows()[0].kind).toBe('existing');
      expect(component.previewRows()[1].kind).toBe('new');
      expect(component.form.controls.subPeriodNames.length).toBe(3);
      expect(component.form.controls.subPeriodNames.at(0).value).toBe('BW1 Apr 2026');
      expect(component.form.controls.subPeriodNames.at(1).value).toBe('BW1 May 2026');
      expect(component.form.controls.subPeriodNames.at(2).value).toBe('BW2 May 2026');
    });

    it('unchecks cascade when startDate changes; uncheck clears preview', () => {
      component.form.controls.startDate.setValue('2026-05-01');
      component.form.controls.cascadeToBiWeekly.setValue(true);
      expect(component.form.controls.cascadeToBiWeekly.value).toBeTrue();

      component.form.controls.startDate.setValue('2026-06-01');

      expect(component.form.controls.cascadeToBiWeekly.value).toBeFalse();
      expect(component.previewRows()).toEqual([]);
      expect(component.form.controls.subPeriodNames.length).toBe(0);
    });

    it('unchecks cascade when endDate changes; does not fire a second fetch', () => {
      component.form.controls.startDate.setValue('2026-05-01');
      component.form.controls.cascadeToBiWeekly.setValue(true);
      service.generateSubPeriods.calls.reset();

      component.form.controls.endDate.setValue('2026-05-30');

      expect(component.form.controls.cascadeToBiWeekly.value).toBeFalse();
      expect(service.generateSubPeriods).not.toHaveBeenCalled();
    });

    it('unchecking by the user clears the preview state', () => {
      component.form.controls.startDate.setValue('2026-05-01');
      component.form.controls.cascadeToBiWeekly.setValue(true);

      component.form.controls.cascadeToBiWeekly.setValue(false);

      expect(component.previewRows()).toEqual([]);
      expect(component.previewLoading()).toBeFalse();
      expect(component.previewError()).toBeNull();
      expect(component.form.controls.subPeriodNames.length).toBe(0);
    });
  });

  // ==================================================================
  // Behaviour — submit paths
  // ==================================================================

  describe('submit — new without cascade', () => {
    beforeEach(async () => {
      await build();
      fixture.detectChanges();
      component.form.patchValue({
        name: 'May 2026',
        frequency: 'MONTHLY',
        startDate: '2026-05-01',
        endDate: '2026-05-31',
      });
    });

    it('calls create with the trimmed body and navigates on success', () => {
      service.create.and.returnValue(of(MONTHLY));

      component.onSubmit();

      expect(service.create).toHaveBeenCalledOnceWith({
        name: 'May 2026',
        startDate: '2026-05-01',
        endDate: '2026-05-31',
        frequency: 'MONTHLY',
      } as PayrollPeriodCreateRequest);
      expect(snackBar.open).toHaveBeenCalledWith(
        'Pay schedule created',
        'Dismiss',
        jasmine.any(Object),
      );
      expect(router.navigate).toHaveBeenCalledWith(['/payroll-periods']);
    });

    it('does not call create when the form is invalid', () => {
      component.form.controls.name.setValue('');

      component.onSubmit();

      expect(service.create).not.toHaveBeenCalled();
    });
  });

  describe('submit — cascade batch', () => {
    beforeEach(async () => {
      service.generateSubPeriods.and.returnValue(of(SUB_RESPONSE));
      await build();
      fixture.detectChanges();
      component.form.patchValue({
        name: 'May 2026',
        frequency: 'MONTHLY',
        startDate: '2026-05-01',
        endDate: '2026-05-31',
      });
      component.form.controls.cascadeToBiWeekly.setValue(true);
    });

    it('sends [monthly, BW1, BW2] with the user-renamed names applied', () => {
      component.form.controls.subPeriodNames.at(1).setValue('BW1 May 2026 renamed');
      component.form.controls.subPeriodNames.at(2).setValue('BW2 May 2026 renamed');

      service.createBatch.and.returnValue(
        of({ succeeded: [MONTHLY], failed: [] }),
      );

      component.onSubmit();

      const batch = service.createBatch.calls.mostRecent()!.args[0];
      expect(batch.length).toBe(3);

      expect(batch[0]).toEqual({
        name: 'May 2026',
        startDate: '2026-05-01',
        endDate: '2026-05-31',
        frequency: 'MONTHLY',
      });

      expect(batch[1].name).toBe('BW1 May 2026 renamed');
      expect(batch[1].frequency).toBe('BI_WEEKLY');
      expect(batch[1].startDate).toBe('2026-05-01');

      expect(batch[2].name).toBe('BW2 May 2026 renamed');
    });

    it('on partial failure: records the failed summaries and stays on the page', () => {
      service.createBatch.and.returnValue(
        of({
          succeeded: [MONTHLY],
          failed: [
            {
              period: {
                name: 'BW2 May 2026',
                startDate: '2026-05-15',
                endDate: '2026-05-28',
                frequency: 'BI_WEEKLY',
              },
              errors: [
                {
                  code: 'PERIOD_RANGE_CONFLICT',
                  message: 'Range conflict',
                  severity: 'ERROR',
                },
              ],
            },
          ],
        }),
      );

      component.onSubmit();

      expect(component.createdCount()).toBe(1);
      expect(component.failedSummaries().length).toBe(1);
      expect(component.failedSummaries()[0].label).toBe('BW2 May 2026');
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  describe('submit — edit', () => {
    it('calls update with the id and navigates on success', async () => {
      makeRouteWithId(MONTHLY.id);
      service.getById.and.returnValue(of(MONTHLY));
      await build();
      fixture.detectChanges();
      service.update.and.returnValue(of({ ...MONTHLY, name: 'May 2026 renamed' }));

      component.form.controls.name.setValue('May 2026 renamed');
      component.onSubmit();

      expect(service.update).toHaveBeenCalledOnceWith(
        MONTHLY.id,
        jasmine.objectContaining({ name: 'May 2026 renamed' }),
      );
      expect(snackBar.open).toHaveBeenCalledWith(
        'Pay schedule updated',
        'Dismiss',
        jasmine.any(Object),
      );
      expect(router.navigate).toHaveBeenCalledWith(['/payroll-periods']);
    });
  });

  // ==================================================================
  // Behaviour — error routing
  // ==================================================================

  describe('error routing', () => {
    beforeEach(async () => {
      await build();
      fixture.detectChanges();
      component.form.patchValue({
        name: 'May 2026',
        frequency: 'MONTHLY',
        startDate: '2026-05-01',
        endDate: '2026-05-31',
      });
    });

    function submitWithError(
      partial: Partial<PayrollPeriodError> & { code: string; message: string },
    ): void {
      service.create.and.returnValue(throwError(() => err(partial)));
      component.onSubmit();
    }

    it('PERIOD_NAME_CONFLICT is surfaced under name', () => {
      submitWithError({
        code: 'DATA_CONFLICT',
        message: 'conflict',
        errors: [
          {
            code: 'PERIOD_NAME_CONFLICT',
            message: 'name taken',
            severity: 'ERROR',
          },
        ],
      });

      expect(component.form.controls.name.getError('serverError')).toBe('name taken');
    });

    it('PERIOD_RANGE_CONFLICT lands on startDate and endDate', () => {
      submitWithError({
        code: 'DATA_CONFLICT',
        message: 'conflict',
        errors: [
          {
            code: 'PERIOD_RANGE_CONFLICT',
            message: 'range overlaps',
            severity: 'ERROR',
          },
        ],
      });

      expect(component.form.controls.startDate.getError('serverError')).toBe(
        'range overlaps',
      );
      expect(component.form.controls.endDate.getError('serverError')).toBe(
        'range overlaps',
      );
    });

    it('PERIOD_CLOSED sets the settled banner and blocks further submits', () => {
      submitWithError({ code: 'PERIOD_CLOSED', message: 'settled' });

      expect(component.formError()).toContain('already settled');
      expect(component.canSubmit()).toBeFalse();
    });

    it('PERIOD_REFERENCED_NAME_ONLY locks the form and shows the amber notice (race-guard path)', () => {
      submitWithError({ code: 'PERIOD_REFERENCED_NAME_ONLY', message: 'referenced' });

      expect(component.nameOnly()).toBeTrue();
      expect(component.form.controls.frequency.disabled).toBeTrue();
      expect(component.form.controls.startDate.disabled).toBeTrue();
      expect(component.form.controls.endDate.disabled).toBeTrue();
      expect(component.form.controls.name.enabled).toBeTrue();
      expect(component.nameOnlyNotice()).toContain('only its name can be changed');
      expect(component.formError()).toBeNull();
    });

    it('an unrecognized error falls back to the raw message', () => {
      submitWithError({ code: 'SERVER_DOWN', message: 'boom' });
      expect(component.formError()).toBe('boom');
    });
  });

  // ==================================================================
  // Behaviour — onCancel
  // ==================================================================

  describe('onCancel', () => {
    beforeEach(async () => {
      await build();
      fixture.detectChanges();
    });

    it('navigates to /payroll-periods', () => {
      component.onCancel();
      expect(router.navigate).toHaveBeenCalledOnceWith(['/payroll-periods']);
    });
  });

  // ==================================================================
  // Template — key rendering branches
  // ==================================================================

  describe('template', () => {
    function byId(id: string): HTMLElement | null {
      return fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
    }

    it('renders the load-error state when the load fails for a non-NOT_FOUND reason', async () => {
      makeRouteWithId(MONTHLY.id);
      service.getById.and.returnValue(
        throwError(() =>
          err({ code: 'NETWORK_OR_SERVER_ERROR', message: 'server down' }),
        ),
      );
      await build();
      fixture.detectChanges();

      expect(byId('payroll-period-editor-load-error')).toBeTruthy();
      expect(byId('payroll-period-form')).toBeNull();
    });

    it('renders the not-found state on NOT_FOUND', async () => {
      makeRouteWithId('missing');
      service.getById.and.returnValue(
        throwError(() => err({ code: 'NOT_FOUND', message: 'gone' })),
      );
      await build();
      fixture.detectChanges();

      expect(byId('payroll-period-editor-not-found')).toBeTruthy();
      expect(byId('payroll-period-form')).toBeNull();
    });

    it('renders the dismissible name-only notice on referenced load', async () => {
      makeRouteWithId(MONTHLY_REFERENCED.id);
      service.getById.and.returnValue(of(MONTHLY_REFERENCED));
      await build();
      fixture.detectChanges();

      const notice = byId('payroll-period-editor-name-only-notice');
      expect(notice).toBeTruthy();
      expect(notice!.textContent).toContain('only its name can be changed');

      const dismissBtn = byId(
        'payroll-period-editor-name-only-notice-dismiss',
      ) as HTMLButtonElement;
      dismissBtn.click();
      fixture.detectChanges();

      expect(byId('payroll-period-editor-name-only-notice')).toBeNull();
    });

    it('renders the form-level error banner for a save error', async () => {
      service.create.and.returnValue(
        throwError(() =>
          err({ code: 'SERVER_DOWN', message: 'server exploded' }),
        ),
      );
      await build();
      fixture.detectChanges();
      component.form.patchValue({
        name: 'May 2026',
        startDate: '2026-05-01',
        endDate: '2026-05-31',
      });
      component.onSubmit();
      fixture.detectChanges();

      expect(byId('payroll-period-editor-error')!.textContent).toContain(
        'server exploded',
      );
    });

    it('renders the partial-batch summary with the failure lines', async () => {
      service.createBatch.and.returnValue(
        of({
          succeeded: [MONTHLY],
          failed: [
            {
              period: {
                name: 'BW2 May 2026',
                startDate: '2026-05-15',
                endDate: '2026-05-28',
                frequency: 'BI_WEEKLY',
              },
              errors: [
                {
                  code: 'X',
                  message: 'rejected',
                  severity: 'ERROR',
                },
              ],
            },
          ],
        }),
      );
      service.generateSubPeriods.and.returnValue(of(SUB_RESPONSE));
      await build();
      fixture.detectChanges();

      component.form.patchValue({
        name: 'May 2026',
        startDate: '2026-05-01',
        endDate: '2026-05-31',
      });
      component.form.controls.cascadeToBiWeekly.setValue(true);
      component.onSubmit();
      fixture.detectChanges();

      expect(byId('payroll-period-editor-partial')).toBeTruthy();
      expect(byId('payroll-period-editor-partial')!.textContent).toContain(
        'BW2 May 2026',
      );
      expect(byId('payroll-period-editor-partial')!.textContent).toContain(
        '1 created',
      );
    });
  });
});
