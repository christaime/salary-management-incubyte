import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { PayrollSummaryEditorPage } from './payroll-summary-editor.page';
import { PayrollSummaryService } from '../../services/payroll-summary.service';
import { PayrollSummaryDetailDto } from '../../models/payroll-summary.model';
import { PayrollSummaryCreateRequest } from '../../models/payroll-summary-request.model';

function makeSummary(
  overrides: Partial<PayrollSummaryDetailDto> = {},
): PayrollSummaryDetailDto {
  return {
    id: 's-1',
    status: 'DRAFT',
    employee: { id: 'emp-1', matricule: 'EMP-0001', name: 'Ada Lovelace' },
    period: {
      id: 'p-1',
      name: 'January 2026',
      startDate: '2026-01-01',
      endDate: '2026-01-31',
      frequency: 'MONTHLY',
      closed: false,
    },
    country: { id: 'c-fr', code: 'FR', name: 'France' },
    department: { id: 'd-eng', code: 'ENG', name: 'Engineering' },
    role: { id: 'r-swe', code: 'SWE', name: 'Software Engineer' },
    employmentType: 'FULL_TIME',
    amounts: {
      base: 1000,
      variable: 100,
      allowances: 50,
      employerTaxes: 200,
      total: 1350,
      currency: 'EUR',
    },
    assignment: {
      id: 'a-1',
      startDate: '2025-10-01',
      endDate: null,
      hasPayroll: true,
    } as any,
    ...overrides,
  };
}

function createRequest(): PayrollSummaryCreateRequest {
  return {
    employeeId: 'emp-1',
    periodId: 'p-1',
    assignmentId: 'a-1',
    base: 1,
    variable: 0,
    allowances: 0,
    employerTaxes: 0,
    total: 1,
    currency: 'EUR',
  };
}

describe('PayrollSummaryEditorPage', () => {
  let fixture: ComponentFixture<PayrollSummaryEditorPage>;
  let component: PayrollSummaryEditorPage;
  let service: jasmine.SpyObj<PayrollSummaryService>;
  let router: Router;

  async function setup(opts: {
    id?: string | null;
    preset?: PayrollSummaryDetailDto | null;
    getById?: PayrollSummaryDetailDto | 'error';
  } = {}): Promise<void> {
    service = jasmine.createSpyObj('PayrollSummaryService', [
      'getById',
      'create',
      'update',
      'selectedNonExistent',
      'clearSelectedNonExistent',
      'setSelectedNonExistent',
    ]);
    service.selectedNonExistent.and.returnValue(opts.preset ?? null);
    service.clearSelectedNonExistent.and.returnValue(undefined);

    if (opts.getById === 'error') {
      service.getById.and.returnValue(throwError(() => ({ message: 'Not found' })));
    } else if (opts.getById) {
      service.getById.and.returnValue(of(opts.getById));
    }

    const paramMap = new Map<string, string>();
    if (opts.id) paramMap.set('id', opts.id);

    await TestBed.configureTestingModule({
      imports: [PayrollSummaryEditorPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: PayrollSummaryService, useValue: service },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: (k: string) => paramMap.get(k) ?? null } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PayrollSummaryEditorPage);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
    fixture.detectChanges();
  }

  // ==================================================================
  // Create mode
  // ==================================================================

  describe('create mode', () => {
    it('starts in loaded state and does not call getById', async () => {
      await setup({ id: null });
      expect(service.getById).not.toHaveBeenCalled();
      expect(component.state()).toBe('loaded');
      expect(component.summary()).toBeNull();
      expect(component.mode()).toBe('create');
    });

    it('does not touch the non-existent stash when it is empty', async () => {
      await setup({ id: null, preset: null });
      expect(service.selectedNonExistent).toHaveBeenCalled();
      expect(service.clearSelectedNonExistent).not.toHaveBeenCalled();
      expect(component.isNonExistent()).toBeFalse();
    });

    it('preloads the summary from the non-existent stash and clears it', async () => {
      const preset = makeSummary();
      await setup({ id: null, preset });
      expect(component.summary()).toBe(preset);
      expect(component.isNonExistent()).toBeTrue();
      expect(service.clearSelectedNonExistent).toHaveBeenCalled();
    });

    it('renders the "New payroll summary" title', async () => {
      await setup({ id: null });
      const el = fixture.nativeElement as HTMLElement;
      const title = el.querySelector('[data-testid="payroll-editor-title"]');
      expect(title?.textContent).toContain('New payroll summary');
    });

    it('create() is called on save', async () => {
      await setup({ id: null });
      const created = makeSummary({ id: 's-new' });
      service.create.and.returnValue(of(created));

      component.onSave(createRequest());

      expect(service.create).toHaveBeenCalledOnceWith(createRequest());
      expect(service.update).not.toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['/payroll-summaries', 's-new']);
    });

    it('create() is called on save even when a preset summary is present', async () => {
      await setup({ id: null, preset: makeSummary() });
      service.create.and.returnValue(of(makeSummary({ id: 's-new' })));

      component.onSave(createRequest());

      expect(service.create).toHaveBeenCalled();
      expect(service.update).not.toHaveBeenCalled();
    });
  });

  // ==================================================================
  // Update mode
  // ==================================================================

  describe('update mode', () => {
    it('loads the summary by id and reaches the loaded state', async () => {
      await setup({ id: 's-1', getById: makeSummary() });
      expect(service.getById).toHaveBeenCalledWith('s-1');
      expect(component.state()).toBe('loaded');
      expect(component.mode()).toBe('update');
      expect(component.summary()?.id).toBe('s-1');
    });

    it('renders the "Edit payroll summary" title', async () => {
      await setup({ id: 's-1', getById: makeSummary() });
      const el = fixture.nativeElement as HTMLElement;
      const title = el.querySelector('[data-testid="payroll-editor-title"]');
      expect(title?.textContent).toContain('Edit payroll summary');
    });

    it('goes to error state when the load fails', async () => {
      await setup({ id: 's-1', getById: 'error' });
      expect(component.state()).toBe('error');
      expect(component.error()).toBe('Not found');
    });

    it('renders the error banner', async () => {
      await setup({ id: 's-1', getById: 'error' });
      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('[data-testid="payroll-editor-error"]')?.textContent)
        .toContain('Not found');
    });

    it('update() is called on save', async () => {
      await setup({ id: 's-1', getById: makeSummary() });
      service.update.and.returnValue(of(makeSummary()));

      component.onSave({
        assignmentId: 'a-1',
        base: 1,
        variable: 0,
        allowances: 0,
        employerTaxes: 0,
        total: 1,
        currency: 'EUR',
      });

      expect(service.update).toHaveBeenCalledWith('s-1', jasmine.any(Object));
      expect(service.create).not.toHaveBeenCalled();
    });

    it('maps a save error to serverErrors and stays on the page', async () => {
      await setup({ id: 's-1', getById: makeSummary() });
      service.update.and.returnValue(
        throwError(() => ({ error: { code: 'SUMMARY_NOT_DRAFT', message: 'Not a draft' } })),
      );

      component.onSave({
        assignmentId: 'a-1',
        base: 1,
        variable: 0,
        allowances: 0,
        employerTaxes: 0,
        total: 1,
        currency: 'EUR',
      });

      expect(component.saving()).toBeFalse();
      expect(component.serverErrors()).toEqual({ __general: 'Not a draft' });
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  // ==================================================================
  // Navigation
  // ==================================================================

  describe('navigation', () => {
    it('onCancel navigates to the detail when a summary exists', async () => {
      await setup({ id: 's-1', getById: makeSummary() });
      component.onCancel();
      expect(router.navigate).toHaveBeenCalledWith(['/payroll-summaries', 's-1']);
    });

    it('onCancel navigates to the list when no summary exists', async () => {
      await setup({ id: null });
      component.onCancel();
      expect(router.navigate).toHaveBeenCalledWith(['/payroll-summaries']);
    });

    it('onBackToList navigates to the list', async () => {
      await setup({ id: null });
      component.onBackToList();
      expect(router.navigate).toHaveBeenCalledWith(['/payroll-summaries']);
    });

    it('the Back button calls onBackToList', async () => {
      await setup({ id: null });
      const el = fixture.nativeElement as HTMLElement;
      el.querySelector<HTMLButtonElement>('[data-testid="payroll-editor-back"]')!
        .click();
      expect(router.navigate).toHaveBeenCalledWith(['/payroll-summaries']);
    });
  });

  // ==================================================================
  // Error mapping
  // ==================================================================

  describe('error mapping', () => {
    it('maps a field-scoped error to the field key', async () => {
      await setup({ id: 's-1', getById: makeSummary() });
      service.update.and.returnValue(
        throwError(() => ({
          error: { code: 'PERIOD_CLOSED', message: 'Period closed' },
        })),
      );
      component.onSave({
        assignmentId: 'a-1',
        base: 1,
        variable: 0,
        allowances: 0,
        employerTaxes: 0,
        total: 1,
        currency: 'EUR',
      });
      expect(component.serverErrors()).toEqual({ periodId: 'Period closed' });
    });

    it('falls back to __general when the code is unknown', async () => {
      await setup({ id: 's-1', getById: makeSummary() });
      service.update.and.returnValue(
        throwError(() => ({ error: { code: 'WHATEVER', message: 'Oops' } })),
      );
      component.onSave({
        assignmentId: 'a-1',
        base: 1,
        variable: 0,
        allowances: 0,
        employerTaxes: 0,
        total: 1,
        currency: 'EUR',
      });
      expect(component.serverErrors()).toEqual({ __general: 'Oops' });
    });

    it('falls back to the top-level message when error.error is absent', async () => {
      await setup({ id: 's-1', getById: makeSummary() });
      service.update.and.returnValue(throwError(() => ({ message: 'Boom' })));
      component.onSave({
        assignmentId: 'a-1',
        base: 1,
        variable: 0,
        allowances: 0,
        employerTaxes: 0,
        total: 1,
        currency: 'EUR',
      });
      expect(component.serverErrors()).toEqual({ __general: 'Boom' });
    });
  });
});
