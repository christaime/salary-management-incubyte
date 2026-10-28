import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PayrollSummaryStatusBadgeComponent } from './payroll-summary-status-badge.component';

describe('PayrollSummaryStatusBadgeComponent', () => {
  let fixture: ComponentFixture<PayrollSummaryStatusBadgeComponent>;
  let component: PayrollSummaryStatusBadgeComponent;

  function setup(status: 'DRAFT' | 'VALIDATED'): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [PayrollSummaryStatusBadgeComponent] });
    fixture = TestBed.createComponent(PayrollSummaryStatusBadgeComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('status', status);
    fixture.detectChanges();
  }

  it('renders a Draft badge', () => {
    setup('DRAFT');
    const el = fixture.nativeElement as HTMLElement;
    const badge = el.querySelector('[data-testid="status-badge-draft"]');
    expect(badge).not.toBeNull();
    expect(badge?.textContent).toContain('Draft');
    expect(badge?.getAttribute('data-status')).toBe('DRAFT');
  });

  it('renders a Validated badge', () => {
    setup('VALIDATED');
    const el = fixture.nativeElement as HTMLElement;
    const badge = el.querySelector('[data-testid="status-badge-validated"]');
    expect(badge).not.toBeNull();
    expect(badge?.textContent).toContain('Validated');
    expect(badge?.getAttribute('data-status')).toBe('VALIDATED');
  });

  it('applies the amber palette for Draft', () => {
    setup('DRAFT');
    const badge = (fixture.nativeElement as HTMLElement)
      .querySelector('[data-testid="status-badge-draft"]')!;
    expect(badge.classList.contains('bg-amber-50')).toBeTrue();
    expect(badge.classList.contains('text-amber-700')).toBeTrue();
  });

  it('applies the emerald palette for Validated', () => {
    setup('VALIDATED');
    const badge = (fixture.nativeElement as HTMLElement)
      .querySelector('[data-testid="status-badge-validated"]')!;
    expect(badge.classList.contains('bg-emerald-50')).toBeTrue();
    expect(badge.classList.contains('text-emerald-700')).toBeTrue();
  });
});
