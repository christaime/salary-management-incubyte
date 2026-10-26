import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';

import { BlockerBannerComponent } from './blocker-banner.component';
import { BaseReportWarning } from '../../services/reports-error';

describe('BlockerBannerComponent', () => {
  let fixture: ComponentFixture<BlockerBannerComponent>;
  let component: BlockerBannerComponent;

  function createWith(blockers: BaseReportWarning[]): void {
    fixture = TestBed.createComponent(BlockerBannerComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('blockers', blockers);
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BlockerBannerComponent, NoopAnimationsModule],
    });
  });

  // -----------------------------------------------------------------
  // Empty state
  // -----------------------------------------------------------------

  it('renders nothing when blockers is empty', () => {
    createWith([]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="blocker-banner"]')).toBeNull();
  });

  // -----------------------------------------------------------------
  // Header and per-code message
  // -----------------------------------------------------------------

  it('renders the banner and the default header on any blocker', () => {
    createWith([
      {
        code: 'PERIOD_CLOSED',
        message: 'Period is closed',
        severity: 'BLOCKER',
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[data-testid="blocker-banner"]')).not.toBeNull();
    expect(el.textContent).toContain('Report Attention Required');
    expect(el.textContent).toContain(
      'Please review and resolve the following data warnings before proceeding.',
    );
    expect(el.textContent).toContain('Period is closed');
  });

  // -----------------------------------------------------------------
  // MISSING_PAYROLL_SUMMARY (with employees)
  // -----------------------------------------------------------------

  it('renders the MISSING_PAYROLL_SUMMARY detail with employees', () => {
    createWith([
      {
        code: 'MISSING_PAYROLL_SUMMARY',
        message: 'Missing summaries',
        severity: 'BLOCKER',
        countConcerned: 2,
        employees: [
          { id: '1', matricule: 'EMP-001', name: 'John Doe' },
          { id: '2', matricule: 'EMP-002', name: 'Jane Roe' },
        ],
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="blocker-message-MISSING_PAYROLL_SUMMARY"]')
        ?.textContent,
    ).toContain('Missing summaries');

    const countEl = el.querySelector(
      '[data-testid="blocker-count-MISSING_PAYROLL_SUMMARY"]',
    ) as HTMLElement;
    expect(countEl.textContent).toContain('2');

    expect(
      el.querySelector('[data-testid="blocker-employee-1"]')?.textContent,
    ).toContain('EMP-001 — John Doe');
    expect(
      el.querySelector('[data-testid="blocker-employee-2"]')?.textContent,
    ).toContain('EMP-002 — Jane Roe');
  });

  it('shows the "+ N more" hint when countConcerned > employees.length', () => {
    createWith([
      {
        code: 'MISSING_PAYROLL_SUMMARY',
        message: 'Missing summaries',
        severity: 'BLOCKER',
        countConcerned: 5,
        employees: [{ id: '1', matricule: 'EMP-001', name: 'John Doe' }],
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    const more = el.querySelector(
      '[data-testid="blocker-more-MISSING_PAYROLL_SUMMARY"]',
    ) as HTMLElement;
    expect(more).not.toBeNull();
    expect(more.textContent).toContain('+ 4 more not shown');
  });

  it('does not show the "+ N more" hint when all concerned employees are listed', () => {
    createWith([
      {
        code: 'MISSING_PAYROLL_SUMMARY',
        message: 'Missing summaries',
        severity: 'BLOCKER',
        countConcerned: 1,
        employees: [{ id: '1', matricule: 'EMP-001', name: 'John Doe' }],
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;
    expect(
      el.querySelector('[data-testid="blocker-more-MISSING_PAYROLL_SUMMARY"]'),
    ).toBeNull();
  });

  // -----------------------------------------------------------------
  // UNVALIDATED_PAYROLL_SUMMARY
  // -----------------------------------------------------------------

  it('renders the UNVALIDATED_PAYROLL_SUMMARY detail', () => {
    createWith([
      {
        code: 'UNVALIDATED_PAYROLL_SUMMARY',
        message: 'Unvalidated summaries',
        severity: 'BLOCKER',
        countConcerned: 3,
        employees: [{ id: '1', matricule: 'EMP-001', name: 'John Doe' }],
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector(
        '[data-testid="blocker-message-UNVALIDATED_PAYROLL_SUMMARY"]',
      )?.textContent,
    ).toContain('Unvalidated summaries');

    expect(
      el.querySelector(
        '[data-testid="blocker-count-UNVALIDATED_PAYROLL_SUMMARY"]',
      )?.textContent,
    ).toContain('3');

    expect(
      el.querySelector('[data-testid="blocker-employee-1"]')?.textContent,
    ).toContain('EMP-001 — John Doe');
  });

  // -----------------------------------------------------------------
  // MISSING_FX_RATE
  // -----------------------------------------------------------------

  it('renders the MISSING_FX_RATE detail with the currency code', () => {
    createWith([
      {
        code: 'MISSING_FX_RATE',
        message: 'Missing FX rate',
        severity: 'BLOCKER',
        currency: 'XAF',
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="blocker-message-MISSING_FX_RATE"]')
        ?.textContent,
    ).toContain('Missing FX rate');
    expect(el.textContent).toContain('Missing rate for:');
    expect(el.textContent).toContain('XAF');
  });

  // -----------------------------------------------------------------
  // MISSING_REFERENCE_CURRENCY
  // -----------------------------------------------------------------

  it('renders the MISSING_REFERENCE_CURRENCY detail with the currency code', () => {
    createWith([
      {
        code: 'MISSING_REFERENCE_CURRENCY',
        message: 'No reference currency',
        severity: 'BLOCKER',
        currency: 'USD',
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    expect(el.textContent).toContain('Reference currency:');
    expect(el.textContent).toContain('USD');
  });

  // -----------------------------------------------------------------
  // PERIOD_CLOSED (message only, no extra details)
  // -----------------------------------------------------------------

  it('renders PERIOD_CLOSED with only the message', () => {
    createWith([
      {
        code: 'PERIOD_CLOSED',
        message: 'Period is closed',
        severity: 'BLOCKER',
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    expect(el.textContent).toContain('Period is closed');
    // No employee chips
    expect(el.querySelector('[data-testid^="blocker-employee-"]')).toBeNull();
    // No count badge
    expect(el.querySelector('[data-testid^="blocker-count-"]')).toBeNull();
  });

  // -----------------------------------------------------------------
  // Multiple blockers
  // -----------------------------------------------------------------

  it('renders one <li> per blocker', () => {
    createWith([
      {
        code: 'MISSING_FX_RATE',
        message: 'Missing FX for XAF',
        severity: 'BLOCKER',
        currency: 'XAF',
      } as BaseReportWarning,
      {
        code: 'PERIOD_CLOSED',
        message: 'Period is closed',
        severity: 'BLOCKER',
      } as BaseReportWarning,
    ]);
    const el = fixture.nativeElement as HTMLElement;

    const items = el.querySelectorAll('li');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('Missing FX for XAF');
    expect(items[1].textContent).toContain('Period is closed');
  });

  // -----------------------------------------------------------------
  // Dismiss
  // -----------------------------------------------------------------

  it('emits dismiss when the close button is clicked', () => {
    createWith([
      {
        code: 'PERIOD_CLOSED',
        message: 'Period is closed',
        severity: 'BLOCKER',
      } as BaseReportWarning,
    ]);

    const emitted: number[] = [];
    component.dismiss.subscribe(() => emitted.push(1));

    const button = fixture.debugElement.query(
      By.css('[data-testid="dismiss-blocker-banner-button"]'),
    );
    expect(button).not.toBeNull();

    (button.nativeElement as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(emitted.length).toBe(1);
  });
});
