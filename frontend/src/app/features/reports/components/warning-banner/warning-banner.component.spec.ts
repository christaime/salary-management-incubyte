import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { WarningBannerComponent } from './warning-banner.component';
import { BaseReportWarning } from '../../services/reports-error';

describe('WarningBannerComponent', () => {
  let fixture: ComponentFixture<WarningBannerComponent>;

  function createWith(warnings: BaseReportWarning[]): void {
    fixture = TestBed.createComponent(WarningBannerComponent);
    fixture.componentRef.setInput('warnings', warnings);
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [WarningBannerComponent, NoopAnimationsModule],
    });
  });

  // -----------------------------------------------------------------
  // Empty state
  // -----------------------------------------------------------------

  it('renders nothing when warnings is empty', () => {
    createWith([]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="warning-banner"]')).toBeNull();
  });

  it('renders nothing when warnings is empty', () => {
    createWith([]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="warning-banner"]')).toBeNull();
  });

  // -----------------------------------------------------------------
  // Default header
  // -----------------------------------------------------------------

  it('renders the header and default structure for any warning', () => {
    createWith([
      {
        code: 'PERIOD_CLOSED',
        message: 'Period is closed',
        severity: 'WARNING',
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[data-testid="warning-banner"]')).not.toBeNull();
    expect(el.textContent).toContain('Data Quality Warnings');
    expect(el.textContent).toContain('Period is closed');
  });

  // -----------------------------------------------------------------
  // MISSING_PAYROLL_SUMMARY (the only code with a details block)
  // -----------------------------------------------------------------

  it('renders MISSING_PAYROLL_SUMMARY with count and employee chips', () => {
    createWith([
      {
        code: 'MISSING_PAYROLL_SUMMARY',
        message: 'Some employees have no payroll summary',
        severity: 'WARNING',
        countConcerned: 2,
        employees: [
          { id: '1', matricule: 'EMP-001', name: 'John Doe' },
          { id: '2', matricule: 'EMP-002', name: 'Jane Roe' },
        ],
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    expect(el.textContent).toContain('Some employees have no payroll summary');
    expect(el.textContent).toContain('total employee(s) affected');

    const countEl = el.querySelector(
      '[data-testid="warn-count-MISSING_PAYROLL_SUMMARY"]',
    ) as HTMLElement;
    expect(countEl.textContent).toContain('2');

    expect(
      el.querySelector('[data-testid="warn-employee-1"]')?.textContent,
    ).toContain('EMP-001');
    expect(
      el.querySelector('[data-testid="warn-employee-2"]')?.textContent,
    ).toContain('EMP-002');
  });

  it('shows the "+ N more" hint when countConcerned > employees.length', () => {
    createWith([
      {
        code: 'MISSING_PAYROLL_SUMMARY',
        message: 'Some employees have no payroll summary',
        severity: 'WARNING',
        countConcerned: 5,
        employees: [{ id: '1', matricule: 'EMP-001', name: 'John Doe' }],
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    // The + N more hint is not data-testid'd in the component
    // (it's a decorative tail). We assert on its text instead.
    expect(el.textContent).toContain('+ 4 more');
  });

  it('does not show the "+ N more" hint when all concerned employees are listed', () => {
    createWith([
      {
        code: 'MISSING_PAYROLL_SUMMARY',
        message: 'Some employees have no payroll summary',
        severity: 'WARNING',
        countConcerned: 1,
        employees: [{ id: '1', matricule: 'EMP-001', name: 'John Doe' }],
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).not.toContain('more');
  });

  // -----------------------------------------------------------------
  // Codes with no detail block
  // -----------------------------------------------------------------

  it('renders UNVALIDATED_PAYROLL_SUMMARY as a message-only entry', () => {
    createWith([
      {
        code: 'UNVALIDATED_PAYROLL_SUMMARY',
        message: 'Some summaries are unvalidated',
        severity: 'WARNING',
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    expect(el.textContent).toContain('Some summaries are unvalidated');
    expect(el.querySelector('[data-testid^="warn-employee-"]')).toBeNull();
    expect(el.querySelector('[data-testid^="warn-count-"]')).toBeNull();
  });

  it('renders MISSING_FX_RATE as a message-only entry (no details block for warnings)', () => {
    createWith([
      {
        code: 'MISSING_FX_RATE',
        message: 'Missing FX rate for XAF',
        severity: 'WARNING',
        currency: 'XAF',
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    expect(el.textContent).toContain('Missing FX rate for XAF');
    // No employee chips: the warning banner does not have a details block
    // for this code (only MISSING_PAYROLL_SUMMARY does).
    expect(el.querySelector('[data-testid^="warn-employee-"]')).toBeNull();
  });

  // -----------------------------------------------------------------
  // Multiple warnings
  // -----------------------------------------------------------------

  it('renders one <li> per warning', () => {
    createWith([
      {
        code: 'MISSING_PAYROLL_SUMMARY',
        message: 'Missing summaries A',
        severity: 'WARNING',
        countConcerned: 1,
        employees: [{ id: '1', matricule: 'EMP-001', name: 'John' }],
      } as BaseReportWarning,
      {
        code: 'MISSING_FX_RATE',
        message: 'Missing FX for XAF',
        severity: 'WARNING',
        currency: 'XAF',
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    const items = el.querySelectorAll('li');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('Missing summaries A');
    expect(items[1].textContent).toContain('Missing FX for XAF');
  });
});
