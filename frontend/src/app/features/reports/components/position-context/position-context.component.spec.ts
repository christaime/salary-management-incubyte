import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PositionContextComponent } from './position-context.component';
import { PositionContextDto } from '../../models';

describe('PositionContextComponent', () => {
  let fixture: ComponentFixture<PositionContextComponent>;
  let component: PositionContextComponent;

  function createWith(
    data: PositionContextDto,
    referenceCurrency = 'USD',
  ): void {
    fixture = TestBed.createComponent(PositionContextComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('data', data);
    fixture.componentRef.setInput('referenceCurrency', referenceCurrency);
    fixture.detectChanges();
  }

  const sample: PositionContextDto = {
    employeeCount: 10,
    min: 33_600,
    p10: 33_600,
    p25: 45_600,
    p50: 66_000,
    p75: 108_000,
    p90: 126_000,
    max: 144_000,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PositionContextComponent],
    });
  });

  it('should create', () => {
    createWith(sample);
    expect(component).toBeTruthy();
  });

  // -----------------------------------------------------------------
  // Header and summary
  // -----------------------------------------------------------------

  it('renders the employee count and the median', () => {
    createWith(sample);
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="position-context-summary"]')?.textContent,
    ).toContain('10 employees in scope');

    expect(
      el.querySelector('[data-testid="position-context-median"]')?.textContent,
    ).toContain('$66,000');
  });

  it('renders the reference currency label', () => {
    createWith(sample, 'EUR');
    const el = fixture.nativeElement as HTMLElement;

    const label = el.querySelector(
      '[data-testid="position-context-currency-label"]',
    )?.textContent;
    expect(label).toContain('EUR');
    expect(label).toContain('Reference currency');
  });

  // -----------------------------------------------------------------
  // Markers
  // -----------------------------------------------------------------

  it('renders 7 markers', () => {
    createWith(sample);
    const el = fixture.nativeElement as HTMLElement;

    const markers = el.querySelectorAll('[data-testid^="marker-"]:not([data-testid^="marker-label"]):not([data-testid^="marker-dot"]):not([data-testid^="marker-amount"])');
    // Easier: count the marker containers directly.
    const containers = el.querySelectorAll('[data-testid^="marker-P"]');
    // markers have labels P0, P10, P25, P50, P75, P90, P100
    // Containers are wrapped in [data-testid="marker-Pxx"]
    // — we query the container (not the label/dot/amount).
    const distinct = new Set<string>();
    containers.forEach((node) => {
      const id = node.getAttribute('data-testid') ?? '';
      // only the container has data-testid="marker-Pxx" (no suffix)
      if (/^marker-P\d+$/.test(id)) distinct.add(id);
    });
    expect(distinct.size).toBe(7);
  });

  it('highlights P50 as the median', () => {
    createWith(sample);
    const el = fixture.nativeElement as HTMLElement;

    const p50 = el.querySelector('[data-testid="marker-P50"]');
    expect(p50?.getAttribute('data-highlighted')).toBe('true');

    const p0 = el.querySelector('[data-testid="marker-P0"]');
    expect(p0?.getAttribute('data-highlighted')).toBe('false');

    const medianLabel = el.querySelector('[data-testid="marker-label-P50"]');
    expect(medianLabel?.textContent).toContain('Median');
  });

  it('renders the amount for every marker', () => {
    createWith(sample);
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="marker-amount-P0"]')?.textContent,
    ).toContain('$33,600');
    expect(
      el.querySelector('[data-testid="marker-amount-P10"]')?.textContent,
    ).toContain('$33,600');
    expect(
      el.querySelector('[data-testid="marker-amount-P25"]')?.textContent,
    ).toContain('$45,600');
    expect(
      el.querySelector('[data-testid="marker-amount-P50"]')?.textContent,
    ).toContain('$66,000');
    expect(
      el.querySelector('[data-testid="marker-amount-P75"]')?.textContent,
    ).toContain('$108,000');
    expect(
      el.querySelector('[data-testid="marker-amount-P90"]')?.textContent,
    ).toContain('$126,000');
    expect(
      el.querySelector('[data-testid="marker-amount-P100"]')?.textContent,
    ).toContain('$144,000');
  });

  it('positions each marker at the correct percentage', () => {
    createWith(sample);
    const el = fixture.nativeElement as HTMLElement;

    const expected: Record<string, string> = {
      P0: '0',
      P10: '10',
      P25: '25',
      P50: '50',
      P75: '75',
      P90: '90',
      P100: '100',
    };

    for (const [label, percent] of Object.entries(expected)) {
      const node = el.querySelector(
        `[data-testid="marker-${label}"]`,
      ) as HTMLElement;
      expect(node.style.left).toBe(`${percent}%`);
    }
  });

  // -----------------------------------------------------------------
  // Formatting
  // -----------------------------------------------------------------

  it('formats amounts in the reference currency', () => {
    createWith(sample, 'EUR');
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="marker-amount-P50"]')?.textContent,
    ).toContain('€66,000');
  });

  it('uses zero decimal places for amounts', () => {
    createWith({
      employeeCount: 10,
      min: 0,
      p10: 0,
      p25: 0,
      p50: 66_000.99,
      p75: 0,
      p90: 0,
      max: 0,
    });
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="marker-amount-P50"]')?.textContent,
    ).toContain('$66,001');
  });

  it('handles a population of 0 gracefully (all zeros)', () => {
    createWith({
      employeeCount: 0,
      min: 0,
      p10: 0,
      p25: 0,
      p50: 0,
      p75: 0,
      p90: 0,
      max: 0,
    });
    const el = fixture.nativeElement as HTMLElement;

    expect(el.textContent).toContain('0 employees in scope');
    expect(
      el.querySelector('[data-testid="marker-amount-P50"]')?.textContent,
    ).toContain('$0');
  });
});
