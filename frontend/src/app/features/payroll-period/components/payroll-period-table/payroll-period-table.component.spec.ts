import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { PayrollPeriodTableComponent } from './payroll-period-table.component';
import { PayrollPeriodDto } from '@features/payroll-period/models';

describe('PayrollPeriodTableComponent', () => {
  let fixture: ComponentFixture<PayrollPeriodTableComponent>;
  let component: PayrollPeriodTableComponent;
  let editSpy: jasmine.Spy<(row: PayrollPeriodDto) => void>;
  let deleteSpy: jasmine.Spy<(row: PayrollPeriodDto) => void>;
  let settleSpy: jasmine.Spy<(row: PayrollPeriodDto) => void>;

  const MONTHLY_OPEN: PayrollPeriodDto = {
    id: 'm-1',
    name: 'May 2026',
    startDate: '2026-05-01',
    endDate: '2026-05-31',
    frequency: 'MONTHLY',
    closed: false,
  };

  const BIWEEKLY_OPEN: PayrollPeriodDto = {
    id: 'bw-1',
    name: 'BW1 May 2026',
    startDate: '2026-05-01',
    endDate: '2026-05-14',
    frequency: 'BI_WEEKLY',
    closed: false,
  };

  const MONTHLY_CLOSED: PayrollPeriodDto = {
    id: 'm-2',
    name: 'April 2026',
    startDate: '2026-04-01',
    endDate: '2026-04-30',
    frequency: 'MONTHLY',
    closed: true,
  };

  const BIWEEKLY_CLOSED: PayrollPeriodDto = {
    id: 'bw-2',
    name: 'BW1 Apr 2026',
    startDate: '2026-04-01',
    endDate: '2026-04-14',
    frequency: 'BI_WEEKLY',
    closed: true,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PayrollPeriodTableComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(PayrollPeriodTableComponent);
    component = fixture.componentInstance;

    editSpy = spyOn(component.editClick, 'emit');
    deleteSpy = spyOn(component.deleteClick, 'emit');
    settleSpy = spyOn(component.settleClick, 'emit');
  });

  // ---------- helpers ----------
  function render(rows: PayrollPeriodDto[], loading = false): void {
    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('loading', loading);
    fixture.detectChanges();
  }

  function row(rowId: string): HTMLTableRowElement {
    return fixture.nativeElement.querySelector(
      `[data-testid="payroll-period-row-${rowId}"]`,
    ) as HTMLTableRowElement;
  }

  function btn(rowId: string, action: 'edit' | 'delete' | 'settle'): HTMLButtonElement | null {
    return fixture.nativeElement.querySelector(
      `[data-testid="row-${action}-${rowId}"]`,
    ) as HTMLButtonElement | null;
  }

  // ==================================================================
  // Behaviour — frequencyLabel
  // ==================================================================

  describe('frequencyLabel', () => {
    it('maps MONTHLY → "Monthly" and BI_WEEKLY → "Bi-weekly"', () => {
      expect(component.frequencyLabel('MONTHLY')).toBe('Monthly');
      expect(component.frequencyLabel('BI_WEEKLY')).toBe('Bi-weekly');
    });
  });

  // ==================================================================
  // Template — loading / empty states
  // ==================================================================

  describe('loading and empty states', () => {
    it('shows the loading row and hides data rows when loading = true', () => {
      render([MONTHLY_OPEN], true);

      expect(
        fixture.nativeElement.querySelector('[data-testid="table-loading"]'),
      ).toBeTruthy();
      expect(
        fixture.nativeElement.querySelector('[data-testid="table-empty"]'),
      ).toBeFalsy();
      expect(row(MONTHLY_OPEN.id)).toBeNull();
    });

    it('shows the empty row when rows is empty and not loading', () => {
      render([], false);

      expect(
        fixture.nativeElement.querySelector('[data-testid="table-empty"]'),
      ).toBeTruthy();
      expect(
        fixture.nativeElement.querySelector('[data-testid="table-loading"]'),
      ).toBeFalsy();
    });
  });

  // ==================================================================
  // Template — row rendering
  // ==================================================================

  describe('row rendering', () => {
    it('renders name, frequency chip, dates, and Open status for an open monthly row', () => {
      render([MONTHLY_OPEN], false);

      const tr = row(MONTHLY_OPEN.id);
      expect(tr).toBeTruthy();

      expect(
        tr.querySelector(`[data-testid="row-name-${MONTHLY_OPEN.id}"]`)?.textContent?.trim(),
      ).toBe('May 2026');

      expect(
        tr.querySelector(`[data-testid="row-frequency-${MONTHLY_OPEN.id}"]`)?.textContent?.trim(),
      ).toBe('Monthly');

      expect(
        tr.querySelector(`[data-testid="row-start-${MONTHLY_OPEN.id}"]`)?.textContent?.trim(),
      ).toBe('2026-05-01');

      expect(
        tr.querySelector(`[data-testid="row-end-${MONTHLY_OPEN.id}"]`)?.textContent?.trim(),
      ).toBe('2026-05-31');

      expect(
        tr.querySelector(`[data-testid="row-open-${MONTHLY_OPEN.id}"]`),
      ).toBeTruthy();
      expect(
        tr.querySelector(`[data-testid="row-settled-${MONTHLY_OPEN.id}"]`),
      ).toBeFalsy();
    });

    it('renders the Settled chip and reflects data-closed for a closed row', () => {
      render([MONTHLY_CLOSED], false);

      const tr = row(MONTHLY_CLOSED.id);
      expect(tr.dataset['closed']).toBe('true');
      expect(
        tr.querySelector(`[data-testid="row-settled-${MONTHLY_CLOSED.id}"]`),
      ).toBeTruthy();
      expect(
        tr.querySelector(`[data-testid="row-open-${MONTHLY_CLOSED.id}"]`),
      ).toBeFalsy();
    });

    it('renders one row per input row, in input order', () => {
      render([MONTHLY_OPEN, BIWEEKLY_OPEN, MONTHLY_CLOSED], false);

      const rowIds = Array.from(
        fixture.nativeElement.querySelectorAll('tbody tr'),
      )
        .map((el) => (el as HTMLElement).dataset['key'])
        .filter(Boolean);

      expect(rowIds).toEqual(['m-1', 'bw-1', 'm-2']);
    });
  });

  // ==================================================================
  // Template — action visibility (the interesting part)
  // ==================================================================

  describe('row-action visibility', () => {
    it('open monthly: Edit, Delete and Settle are all rendered and enabled', () => {
      render([MONTHLY_OPEN], false);

      const edit = btn(MONTHLY_OPEN.id, 'edit');
      const del = btn(MONTHLY_OPEN.id, 'delete');
      const settle = btn(MONTHLY_OPEN.id, 'settle');

      expect(edit).toBeTruthy();
      expect(edit!.disabled).toBeFalse();
      expect(del).toBeTruthy();
      expect(del!.disabled).toBeFalse();
      expect(settle).toBeTruthy();
      expect(settle!.disabled).toBeFalse();
    });

    it('open bi-weekly: Edit and Delete rendered; Settle NOT rendered', () => {
      render([BIWEEKLY_OPEN], false);

      expect(btn(BIWEEKLY_OPEN.id, 'edit')).toBeTruthy();
      expect(btn(BIWEEKLY_OPEN.id, 'delete')).toBeTruthy();
      expect(btn(BIWEEKLY_OPEN.id, 'settle')).toBeNull();
    });

    it('closed monthly: Edit and Delete are rendered but disabled; Settle NOT rendered', () => {
      render([MONTHLY_CLOSED], false);

      const edit = btn(MONTHLY_CLOSED.id, 'edit');
      const del = btn(MONTHLY_CLOSED.id, 'delete');

      expect(edit).toBeTruthy();
      expect(edit!.disabled).toBeTrue();
      expect(del).toBeTruthy();
      expect(del!.disabled).toBeTrue();
      expect(btn(MONTHLY_CLOSED.id, 'settle')).toBeNull();
    });

    it('closed bi-weekly: Edit and Delete rendered but disabled; Settle NOT rendered', () => {
      render([BIWEEKLY_CLOSED], false);

      expect(btn(BIWEEKLY_CLOSED.id, 'edit')!.disabled).toBeTrue();
      expect(btn(BIWEEKLY_CLOSED.id, 'delete')!.disabled).toBeTrue();
      expect(btn(BIWEEKLY_CLOSED.id, 'settle')).toBeNull();
    });
  });

  // ==================================================================
  // Template — output emission
  // ==================================================================

  describe('row-action emissions', () => {
    it('Edit emits the row on click', () => {
      render([MONTHLY_OPEN], false);
      btn(MONTHLY_OPEN.id, 'edit')!.click();
      expect(editSpy).toHaveBeenCalledOnceWith(MONTHLY_OPEN);
    });

    it('Delete emits the row on click', () => {
      render([MONTHLY_OPEN], false);
      btn(MONTHLY_OPEN.id, 'delete')!.click();
      expect(deleteSpy).toHaveBeenCalledOnceWith(MONTHLY_OPEN);
    });

    it('Settle emits the row on click for an open monthly', () => {
      render([MONTHLY_OPEN], false);
      btn(MONTHLY_OPEN.id, 'settle')!.click();
      expect(settleSpy).toHaveBeenCalledOnceWith(MONTHLY_OPEN);
    });

    it('does NOT emit edit / delete on click when the row is closed', () => {
      render([MONTHLY_CLOSED], false);

      // Disabled buttons don't fire click handlers in the browser.
      btn(MONTHLY_CLOSED.id, 'edit')!.click();
      btn(MONTHLY_CLOSED.id, 'delete')!.click();

      expect(editSpy).not.toHaveBeenCalled();
      expect(deleteSpy).not.toHaveBeenCalled();
    });
  });
});
