import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EarnerSpectrumComponent } from './earner-spectrum.component';
import { EarnerRankingDto } from '../../models';

describe('EarnerSpectrumComponent', () => {
  let fixture: ComponentFixture<EarnerSpectrumComponent>;
  let component: EarnerSpectrumComponent;

  function makeEarner(
    rank: number,
    monthly: number,
    percentile: number | null,
    frequency: 'MONTHLY' | 'BI_WEEKLY' = 'MONTHLY',   // ← parametrized
  ): EarnerRankingDto {
    return {
      monthlyReferenceAmount: monthly,
      frequency,
      percentile,
      rank,
      employee: {
        id: `e-${rank}`,
        matricule: `EMP-${rank}`,
        name: `Employee ${rank}`,
      },
      role: { id: 'r', code: 'DEV', name: 'Developer' },
      department: { id: 'd', code: 'ENG', name: 'Engineering' },
      country: { id: 'c', code: 'US', name: 'United States' },
      employmentType: 'FULL_TIME',
      period: {
        id: 'p-1',
        name: 'January 2026',
        startDate: '2026-01-01',
        endDate: '2026-01-31',
        frequency: 'MONTHLY',
        closed: false,
      },
    };
  }

  function createWith(overrides: Partial<{
    tops: EarnerRankingDto[];
    bottoms: EarnerRankingDto[];
    topTotalAnnual: number;
    bottomTotalAnnual: number;
    referenceCurrency: string;
    frequency: 'MONTHLY' | 'BI_WEEKLY';
  }> = {}): void {
    fixture = TestBed.createComponent(EarnerSpectrumComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('tops', overrides.tops ?? [
      makeEarner(1, 12_000, 9999),
      makeEarner(2, 10_000, 9900),
    ]);
    fixture.componentRef.setInput('bottoms', overrides.bottoms ?? [
      makeEarner(999, 1_500, 10),
      makeEarner(1000, 1_200, 1),
    ]);
    fixture.componentRef.setInput('topTotalAnnual', overrides.topTotalAnnual ?? 264_000);
    fixture.componentRef.setInput('bottomTotalAnnual', overrides.bottomTotalAnnual ?? 32_400);
    fixture.componentRef.setInput('referenceCurrency', overrides.referenceCurrency ?? 'USD');
    fixture.componentRef.setInput('frequency', overrides.frequency ?? 'MONTHLY');
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EarnerSpectrumComponent],
    });
  });

  it('should create', () => {
    createWith();
    expect(component).toBeTruthy();
  });

  // -----------------------------------------------------------------
  // Card presence
  // -----------------------------------------------------------------

  it('renders both top and bottom cards', () => {
    createWith();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[data-testid="top-card"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="bottom-card"]')).not.toBeNull();
  });

  // -----------------------------------------------------------------
  // Rows
  // -----------------------------------------------------------------

  it('renders the correct number of rows in each card', () => {
    createWith();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelectorAll('[data-testid="top-row"]').length).toBe(2);
    expect(el.querySelectorAll('[data-testid="bottom-row"]').length).toBe(2);
  });

  it('renders each row with its employee id and rank', () => {
    createWith();
    const el = fixture.nativeElement as HTMLElement;

    const topRows = el.querySelectorAll('[data-testid="top-row"]');
    expect(topRows[0].getAttribute('data-employee-id')).toBe('e-1');
    expect(topRows[0].getAttribute('data-rank')).toBe('1');
    expect(topRows[1].getAttribute('data-employee-id')).toBe('e-2');
    expect(topRows[1].getAttribute('data-rank')).toBe('2');
  });

  // -----------------------------------------------------------------
  // Employee name and matricule
  // -----------------------------------------------------------------

  it('renders the employee name and matricule for top rows', () => {
    createWith();
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="top-name-e-1"]')?.textContent,
    ).toContain('Employee 1');
    expect(
      el.querySelector('[data-testid="top-name-e-2"]')?.textContent,
    ).toContain('Employee 2');
  });

  // -----------------------------------------------------------------
  // Formatting
  // -----------------------------------------------------------------

  it('formats annual as monthly × 12', () => {
    createWith();
    const el = fixture.nativeElement as HTMLElement;

    // 12,000 × 12 = 144,000
    expect(
      el.querySelector('[data-testid="top-annual-e-1"]')?.textContent,
    ).toContain('$144,000');
    // 10,000 × 12 = 120,000
    expect(
      el.querySelector('[data-testid="top-annual-e-2"]')?.textContent,
    ).toContain('$120,000');
  });

  it('formats monthly with cents and the correct cadence label', () => {
    createWith();
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="top-monthly-e-1"]')?.textContent,
    ).toContain('$12,000.00');
    expect(
      el.querySelector('[data-testid="top-monthly-e-1"]')?.textContent,
    ).toContain('/ mo');
  });

  it('uses "/ bi-wk" as the cadence label for BI_WEEKLY items', () => {
    createWith({
      tops: [makeEarner(1, 12_000, 9999, 'BI_WEEKLY')],
      bottoms: [makeEarner(999, 1_500, 10, 'BI_WEEKLY')],
    });
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="top-monthly-e-1"]')?.textContent,
    ).toContain('/ bi-wk');
  });

  it('formats percentile as Pxx.xx', () => {
    createWith();
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="top-percentile-e-1"]')?.textContent?.trim(),
    ).toBe('P99.99');
    expect(
      el.querySelector('[data-testid="top-percentile-e-2"]')?.textContent?.trim(),
    ).toBe('P99.00');
  });

  it('renders "—" for null percentile', () => {
    createWith({
      tops: [makeEarner(1, 12_000, null)],
    });
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="top-percentile-e-1"]')?.textContent?.trim(),
    ).toBe('—');
  });

  // -----------------------------------------------------------------
  // Aggregates
  // -----------------------------------------------------------------

  it('renders the aggregate total for each card', () => {
    createWith({ topTotalAnnual: 264_000, bottomTotalAnnual: 32_400 });
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="top-total-annual"]')?.textContent,
    ).toContain('$264,000');
    expect(
      el.querySelector('[data-testid="bottom-total-annual"]')?.textContent,
    ).toContain('$32,400');
  });

  // -----------------------------------------------------------------
  // Ranges
  // -----------------------------------------------------------------

  it('renders the min/max range for each card', () => {
    createWith({
      tops: [makeEarner(1, 10_000, 9999), makeEarner(2, 20_000, 9900)],
      bottoms: [makeEarner(999, 1_000, 10), makeEarner(1000, 500, 1)],
    });
    const el = fixture.nativeElement as HTMLElement;

    // Top: 10k×12 = 120k .. 20k×12 = 240k
    expect(
      el.querySelector('[data-testid="top-range"]')?.textContent,
    ).toContain('$120,000');
    expect(
      el.querySelector('[data-testid="top-range"]')?.textContent,
    ).toContain('$240,000');

    // Bottom: 500×12 = 6k .. 1000×12 = 12k
    expect(
      el.querySelector('[data-testid="bottom-range"]')?.textContent,
    ).toContain('$6,000');
    expect(
      el.querySelector('[data-testid="bottom-range"]')?.textContent,
    ).toContain('$12,000');
  });

  it('handles empty arrays without crashing', () => {
    createWith({ tops: [], bottoms: [] });
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelectorAll('[data-testid="top-row"]').length).toBe(0);
    expect(el.querySelectorAll('[data-testid="bottom-row"]').length).toBe(0);
    expect(
      el.querySelector('[data-testid="top-range"]')?.textContent,
    ).toContain('$0');
    expect(
      el.querySelector('[data-testid="bottom-range"]')?.textContent,
    ).toContain('$0');
  });

  // -----------------------------------------------------------------
  // Currency
  // -----------------------------------------------------------------

  it('uses the given referenceCurrency', () => {
    createWith({ referenceCurrency: 'EUR' });
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="top-annual-e-1"]')?.textContent,
    ).toContain('€144,000');
    expect(
      el.querySelector('[data-testid="top-monthly-e-1"]')?.textContent,
    ).toContain('€12,000.00');
  });

  // -----------------------------------------------------------------
  // countryFlag
  // -----------------------------------------------------------------

  it('renders the flag emoji for a valid country code', () => {
    createWith();
    const el = fixture.nativeElement as HTMLElement;

    // United States → 🇺🇸
    expect(el.textContent).toContain('🇺🇸');
  });

  // -----------------------------------------------------------------
  // Pure helpers
  // -----------------------------------------------------------------

  it('annual() returns monthly × 12', () => {
    createWith();
    const item = makeEarner(1, 12_000, 9999);
    expect(component.annual(item)).toBe(144_000);
  });

  it('formatPercentile() pads to 2 decimals', () => {
    createWith();
    expect(component.formatPercentile(0)).toBe('P0.00');
    expect(component.formatPercentile(4)).toBe('P0.04');
    expect(component.formatPercentile(9966)).toBe('P99.66');
    expect(component.formatPercentile(10000)).toBe('P100.00');
    expect(component.formatPercentile(null)).toBe('—');
  });

  it('cadenceLabel() returns the right suffix', () => {
    createWith();   // ← REQUIRED: input.required() needs an injection context
    expect(component.cadenceLabel('MONTHLY')).toBe('/ mo');
    expect(component.cadenceLabel('BI_WEEKLY')).toBe('/ bi-wk');
  });

  it('countryFlag() converts a 2-letter code to a flag emoji', () => {
    createWith();
    expect(component.countryFlag('US')).toBe('🇺🇸');
    expect(component.countryFlag('FR')).toBe('🇫🇷');
    expect(component.countryFlag(null)).toBe('');
    expect(component.countryFlag('XYZ')).toBe('');
  });
});
