import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  Router,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

import { PayrollSummaryDetailPage } from './payroll-summary-detail.page';
import { PayrollSummaryService } from '../../services/payroll-summary.service';
import { PayrollSummaryDetailDto } from '../../models/payroll-summary.model';

function makeSummary(
  overrides: Partial<PayrollSummaryDetailDto> = {},
): PayrollSummaryDetailDto {
  return {
    id: 's-1',
    status: 'DRAFT',
    employee: { id: 'e-1', matricule: 'EMP-0001', name: 'Ada Lovelace' },
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
    },
    ...overrides,
  };
}

function provideRouteId(id: string | null) {
  return {
    provide: ActivatedRoute,
    useValue: {
      paramMap: of(convertToParamMap(id ? { id } : {})),
      snapshot: { paramMap: convertToParamMap(id ? { id } : {}) },
    },
  };
}

describe('PayrollSummaryDetailPage', () => {
  let fixture: ComponentFixture<PayrollSummaryDetailPage>;
  let component: PayrollSummaryDetailPage;
  let service: jasmine.SpyObj<PayrollSummaryService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let router: Router;

  async function setup(
    id: string | null,
    summary?: PayrollSummaryDetailDto,
  ): Promise<void> {
    service = jasmine.createSpyObj('PayrollSummaryService', [
      'getById',
      'validate',
      'delete',
    ]);
    if (summary) service.getById.and.returnValue(of(summary));
    service.validate.and.returnValue(of(summary!));
    service.delete.and.returnValue(of(void 0));

    dialog = jasmine.createSpyObj('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [PayrollSummaryDetailPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideRouteId(id),
        { provide: PayrollSummaryService, useValue: service },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PayrollSummaryDetailPage);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
    fixture.detectChanges();
  }

  // ------------------------------------------------------------------
  // Loading / error
  // ------------------------------------------------------------------

  it('calls getById on init', async () => {
    await setup('s-1', makeSummary());
    expect(service.getById).toHaveBeenCalledWith('s-1');
  });

  it('renders the error banner when loading fails', async () => {
    service = jasmine.createSpyObj('PayrollSummaryService', ['getById']);
    service.getById.and.returnValue(
      throwError(() => ({ code: 'NOT_FOUND', message: 'Not found' })),
    );
    dialog = jasmine.createSpyObj('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [PayrollSummaryDetailPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideRouteId('s-1'),
        { provide: PayrollSummaryService, useValue: service },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PayrollSummaryDetailPage);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.state()).toBe('error');
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="payroll-detail-error"]')?.textContent)
      .toContain('Not found');
  });

  // ------------------------------------------------------------------
  // Success — DRAFT
  // ------------------------------------------------------------------

 it('renders the placement card', async () => {
   await setup('s-1', makeSummary());
   const el = fixture.nativeElement as HTMLElement;
   const placement = el.querySelector('[data-testid="payroll-detail-placement"]')!;
   expect(placement.textContent).toContain('France');
   expect(placement.textContent).toContain('FR');
   expect(placement.textContent).toContain('Engineering');
   expect(placement.textContent).toContain('Software Engineer');
   expect(placement.textContent).toContain('SWE');
   expect(placement.textContent).toContain('Full time');   // ← was 'Full-time Permanent'
   expect(placement.textContent).toContain('2025-10-01');
   expect(placement.textContent).toContain('Open-ended');
 });

  it('renders the amounts card with all five rows', async () => {
    await setup('s-1', makeSummary());
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="amount-base"]')?.textContent).toContain('€1,000.00');
    expect(el.querySelector('[data-testid="amount-variable"]')?.textContent).toContain('€100.00');
    expect(el.querySelector('[data-testid="amount-allowances"]')?.textContent).toContain('€50.00');
    expect(el.querySelector('[data-testid="amount-employer-taxes"]')?.textContent).toContain('€200.00');
    expect(el.querySelector('[data-testid="amount-total"]')?.textContent).toContain('€1,350.00');
  });

  it('renders the DRAFT status badge', async () => {
    await setup('s-1', makeSummary());
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="status-badge-draft"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="status-badge-validated"]')).toBeNull();
  });

  it('renders Edit, Delete, Validate buttons on DRAFT', async () => {
    await setup('s-1', makeSummary({ status: 'DRAFT' }));
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="payroll-detail-edit"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="payroll-detail-delete"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="payroll-detail-validate"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="payroll-detail-final-note"]')).toBeNull();
  });

  // ------------------------------------------------------------------
  // Success — VALIDATED
  // ------------------------------------------------------------------

  it('renders no action buttons on VALIDATED', async () => {
    await setup('s-1', makeSummary({ status: 'VALIDATED' }));
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="payroll-detail-edit"]')).toBeNull();
    expect(el.querySelector('[data-testid="payroll-detail-delete"]')).toBeNull();
    expect(el.querySelector('[data-testid="payroll-detail-validate"]')).toBeNull();
    expect(el.querySelector('[data-testid="payroll-detail-final-note"]')?.textContent)
      .toContain('final');
  });

  it('renders the VALIDATED status badge', async () => {
    await setup('s-1', makeSummary({ status: 'VALIDATED' }));
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="status-badge-validated"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="status-badge-draft"]')).toBeNull();
  });

  // ------------------------------------------------------------------
  // Total mismatch
  // ------------------------------------------------------------------

  it('shows the mismatch warning when total does not equal the sum', async () => {
    const s = makeSummary();
    s.amounts.total = 999;
    await setup('s-1', s);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="total-mismatch-warning"]')).not.toBeNull();
  });

  it('does not show the mismatch warning when total matches', async () => {
    await setup('s-1', makeSummary());
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="total-mismatch-warning"]')).toBeNull();
  });

  // ------------------------------------------------------------------
  // Navigation
  // ------------------------------------------------------------------

  it('navigates back to the list on back button', async () => {
    await setup('s-1', makeSummary());
    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('[data-testid="payroll-detail-back"]') as HTMLButtonElement).click();
    expect(router.navigate).toHaveBeenCalledWith(['/payroll-summaries']);
  });

  it('navigates to the editor on Edit', async () => {
    await setup('s-1', makeSummary({ status: 'DRAFT' }));
    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('[data-testid="payroll-detail-edit"]') as HTMLButtonElement).click();
    expect(router.navigate).toHaveBeenCalledWith(['/payroll-summaries', 's-1', 'edit']);
  });

  // ------------------------------------------------------------------
  // Validate
  // ------------------------------------------------------------------

  it('opens the confirm dialog on Validate and calls validate on confirm', async () => {
    await setup('s-1', makeSummary({ status: 'DRAFT' }));
    dialog.open.and.returnValue({ afterClosed: () => of(true) } as any);

    component.onValidate();

    expect(dialog.open).toHaveBeenCalled();
    expect(service.validate).toHaveBeenCalledWith('s-1');
  });

  it('does not call validate when the confirm dialog is dismissed', async () => {
    await setup('s-1', makeSummary({ status: 'DRAFT' }));
    dialog.open.and.returnValue({ afterClosed: () => of(false) } as any);

    component.onValidate();

    expect(service.validate).not.toHaveBeenCalled();
  });

  it('does nothing when Validate is called on a VALIDATED summary', async () => {
    await setup('s-1', makeSummary({ status: 'VALIDATED' }));
    dialog.open.and.returnValue({ afterClosed: () => of(true) } as any);

    component.onValidate();

    expect(dialog.open).not.toHaveBeenCalled();
    expect(service.validate).not.toHaveBeenCalled();
  });

  // ------------------------------------------------------------------
  // Delete
  // ------------------------------------------------------------------

  it('opens the confirm dialog on Delete and calls delete on confirm', async () => {
    await setup('s-1', makeSummary({ status: 'DRAFT' }));
    dialog.open.and.returnValue({ afterClosed: () => of(true) } as any);

    component.onDelete();

    expect(service.delete).toHaveBeenCalledWith('s-1');
    expect(router.navigate).toHaveBeenCalledWith(['/payroll-summaries']);
  });

  it('does not call delete when the confirm dialog is dismissed', async () => {
    await setup('s-1', makeSummary({ status: 'DRAFT' }));
    dialog.open.and.returnValue({ afterClosed: () => of(false) } as any);

    component.onDelete();

    expect(service.delete).not.toHaveBeenCalled();
  });
});
