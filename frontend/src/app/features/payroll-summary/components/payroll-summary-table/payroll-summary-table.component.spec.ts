import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { PayrollSummaryTableComponent } from './payroll-summary-table.component';
import { PayrollSummaryDto } from '../../models/payroll-summary.model';

function makeRow(overrides: Partial<PayrollSummaryDto> = {}): PayrollSummaryDto {
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
    // NEW: required by PayrollSummaryDto
    assignment: {
      id: 'a-1',
      startDate: '2025-10-01',
      endDate: null,
      //hasPayroll: true,
      frequency: 'MONTHLY',
    },
    ...overrides,
  };
}
describe('PayrollSummaryTableComponent', () => {
  let fixture: ComponentFixture<PayrollSummaryTableComponent>;
  let component: PayrollSummaryTableComponent;

  function createWith(rows: PayrollSummaryDto[], loading = false): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [PayrollSummaryTableComponent, NoopAnimationsModule],
    });
    fixture = TestBed.createComponent(PayrollSummaryTableComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('loading', loading);
    fixture.detectChanges();
  }

  // ------------------------------------------------------------------
  // Loading / empty
  // ------------------------------------------------------------------

  it('renders the loading row when loading is true', () => {
    createWith([], true);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="table-loading"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="table-empty"]')).toBeNull();
  });

  it('renders the empty row when there are no rows and not loading', () => {
    createWith([], false);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="table-empty"]')).not.toBeNull();
    expect(el.querySelectorAll('[data-testid^="payroll-row-"]').length).toBe(0);
  });

  it('renders one row per summary', () => {
    createWith([makeRow({ id: 's-1' }), makeRow({ id: 's-2' })]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('[data-testid^="payroll-row-"]').length).toBe(2);
  });

  // ------------------------------------------------------------------
  // Row content
  // ------------------------------------------------------------------

  it('renders employee name and matricule', () => {
    createWith([makeRow()]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="row-employee-name-s-1"]')?.textContent)
      .toContain('Ada Lovelace');
    expect(el.querySelector('[data-testid="row-employee-matricule-s-1"]')?.textContent)
      .toContain('EMP-0001');
  });

  it('renders period name and dates', () => {
    createWith([makeRow()]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="row-period-name-s-1"]')?.textContent)
      .toContain('January 2026');
    expect(el.querySelector('[data-testid="row-period-range-s-1"]')?.textContent)
      .toContain('2026-01-01');
    expect(el.querySelector('[data-testid="row-period-range-s-1"]')?.textContent)
      .toContain('2026-01-31');
  });

  it('renders the combined placement block', () => {
    createWith([makeRow()]);
    const el = fixture.nativeElement as HTMLElement;
    const row = el.querySelector('[data-testid="payroll-row-s-1"]')!;
    const text = row.textContent ?? '';

    expect(text).toContain('FR');
    expect(text).toContain('France');
    expect(text).toContain('Engineering');
    expect(text).toContain('Software Engineer');
    expect(text).toContain('SWE');
    expect(el.querySelector('[data-testid="row-employment-type-s-1"]')?.textContent)
      .toContain('Full time');   // ← was 'Full-time Permanent'
  });

  it('renders the country flag emoji', () => {
    createWith([makeRow()]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="row-country-s-1"]')?.textContent?.trim())
      .toBe('🇫🇷');
  });

  it('formats the total with the row currency', () => {
    createWith([makeRow()]);
    const el = fixture.nativeElement as HTMLElement;
    const total = el.querySelector('[data-testid="row-total-s-1"]');
    expect(total?.textContent).toContain('€');
    expect(total?.textContent).toContain('1,350.00');
    expect(total?.getAttribute('data-currency')).toBe('EUR');
    expect(total?.getAttribute('data-amount')).toBe('1350');
  });

  it('formats a non-EUR currency', () => {
    createWith([makeRow({
      amounts: { base: 0, variable: 0, allowances: 0, employerTaxes: 0, total: 2000, currency: 'USD' },
    })]);
    const el = fixture.nativeElement as HTMLElement;
    const total = el.querySelector('[data-testid="row-total-s-1"]');
    expect(total?.textContent).toContain('$');
    expect(total?.textContent).toContain('2,000.00');
  });

  it('renders the Draft status badge', () => {
    createWith([makeRow({ status: 'DRAFT' })]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="status-badge-draft"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="status-badge-validated"]')).toBeNull();
  });

  it('renders the Validated status badge', () => {
    createWith([makeRow({ status: 'VALIDATED' })]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="status-badge-validated"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="status-badge-draft"]')).toBeNull();
  });

  // ------------------------------------------------------------------
  // Row actions — DRAFT
  // ------------------------------------------------------------------

  it('renders Edit, Validate, Delete on DRAFT rows', () => {
    createWith([makeRow({ status: 'DRAFT' })]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="row-edit-s-1"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="row-validate-s-1"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="row-delete-s-1"]')).not.toBeNull();
  });

  it('emits editClick when Edit is clicked', () => {
    const row = makeRow({ status: 'DRAFT' });
    createWith([row]);
    const spy = jasmine.createSpy('edit');
    component.editClick.subscribe(spy);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[data-testid="row-edit-s-1"]')!
      .click();

    expect(spy).toHaveBeenCalledOnceWith(row);
  });

  it('emits validateClick when Validate is clicked', () => {
    const row = makeRow({ status: 'DRAFT' });
    createWith([row]);
    const spy = jasmine.createSpy('validate');
    component.validateClick.subscribe(spy);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[data-testid="row-validate-s-1"]')!
      .click();

    expect(spy).toHaveBeenCalledOnceWith(row);
  });

  it('emits deleteClick when Delete is clicked', () => {
    const row = makeRow({ status: 'DRAFT' });
    createWith([row]);
    const spy = jasmine.createSpy('delete');
    component.deleteClick.subscribe(spy);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[data-testid="row-delete-s-1"]')!
      .click();

    expect(spy).toHaveBeenCalledOnceWith(row);
  });

  // ------------------------------------------------------------------
  // Row actions — VALIDATED
  // ------------------------------------------------------------------

  it('hides Edit, Validate, Delete on VALIDATED rows', () => {
    createWith([makeRow({ status: 'VALIDATED' })]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="row-edit-s-1"]')).toBeNull();
    expect(el.querySelector('[data-testid="row-validate-s-1"]')).toBeNull();
    expect(el.querySelector('[data-testid="row-delete-s-1"]')).toBeNull();
    expect(el.textContent).toContain('Final');
  });

  // ------------------------------------------------------------------
  // Row click
  // ------------------------------------------------------------------

  it('emits rowClick when the row body is clicked', () => {
    const row = makeRow();
    createWith([row]);
    const spy = jasmine.createSpy('rowClick');
    component.rowClick.subscribe(spy);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLTableRowElement>('[data-testid="payroll-row-s-1"]')!
      .click();

    expect(spy).toHaveBeenCalledOnceWith(row);
  });

  it('does NOT emit rowClick when an action button is clicked', () => {
    const row = makeRow({ status: 'DRAFT' });
    createWith([row]);
    const rowClickSpy = jasmine.createSpy('rowClick');
    const editSpy = jasmine.createSpy('edit');
    component.rowClick.subscribe(rowClickSpy);
    component.editClick.subscribe(editSpy);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[data-testid="row-edit-s-1"]')!
      .click();

    expect(editSpy).toHaveBeenCalledTimes(1);
    expect(rowClickSpy).not.toHaveBeenCalled();
  });

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  it('countryFlag converts ISO codes to emoji', () => {
    createWith([]);
    expect(component.countryFlag('FR')).toBe('🇫🇷');
    expect(component.countryFlag('DE')).toBe('🇩🇪');
    expect(component.countryFlag(null)).toBe('');
    expect(component.countryFlag('XYZ')).toBe('');
  });

  it('employmentTypeLabel maps every enum value to the canonical label', () => {
    createWith([]);
    expect(component.employmentTypeLabel('FULL_TIME')).toBe('Full time');
    expect(component.employmentTypeLabel('PART_TIME')).toBe('Part time');
    expect(component.employmentTypeLabel('CONTRACTOR')).toBe('Contractor');
  });
});
