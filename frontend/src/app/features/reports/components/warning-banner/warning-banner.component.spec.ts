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
  // MISSING_PAYROLL_SUMMARY
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
    expect(el.textContent).toContain('payroll summaries missing for employees');

    const countEl = el.querySelector(
      '[data-testid="warn-count-MISSING_PAYROLL_SUMMARY"]',
    ) as HTMLElement;
    expect(countEl.textContent).toContain('2');

    expect(
      el.querySelector('[data-testid="warn-employee-1"]')?.textContent,
    ).toContain('EMP-001 — John Doe');
    expect(
      el.querySelector('[data-testid="warn-employee-2"]')?.textContent,
    ).toContain('EMP-002 — Jane Roe');
  });

  it('shows the "… and more" hint when employees.length > 5', () => {
    createWith([
      {
        code: 'MISSING_PAYROLL_SUMMARY',
        message: 'Some employees have no payroll summary',
        severity: 'WARNING',
        countConcerned: 8,
        employees: [
          { id: '1', matricule: 'EMP-001', name: 'A' },
          { id: '2', matricule: 'EMP-002', name: 'B' },
          { id: '3', matricule: 'EMP-003', name: 'C' },
          { id: '4', matricule: 'EMP-004', name: 'D' },
          { id: '5', matricule: 'EMP-005', name: 'E' },
          { id: '6', matricule: 'EMP-006', name: 'F' },
        ],
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    // First 5 chips render
    expect(el.querySelector('[data-testid="warn-employee-5"]')).not.toBeNull();
    // 6th is sliced off
    expect(el.querySelector('[data-testid="warn-employee-6"]')).toBeNull();
    // Truncation hint is shown
    const more = el.querySelector(
      '[data-testid="warn-more-MISSING_PAYROLL_SUMMARY"]',
    ) as HTMLElement;
    expect(more).not.toBeNull();
    expect(more.textContent).toContain('… and more');
  });

  it('does not show the "… and more" hint when employees.length <= 5', () => {
    createWith([
      {
        code: 'MISSING_PAYROLL_SUMMARY',
        message: 'Some employees have no payroll summary',
        severity: 'WARNING',
        countConcerned: 3,
        employees: [
          { id: '1', matricule: 'EMP-001', name: 'A' },
          { id: '2', matricule: 'EMP-002', name: 'B' },
          { id: '3', matricule: 'EMP-003', name: 'C' },
        ],
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;
    expect(
      el.querySelector('[data-testid="warn-more-MISSING_PAYROLL_SUMMARY"]'),
    ).toBeNull();
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
