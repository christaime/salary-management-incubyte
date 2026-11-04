import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import {
  PayrollPeriodFilterBarComponent,
  PayrollPeriodFilterValue,
} from './payroll-period-filter-bar.component';

describe('PayrollPeriodFilterBarComponent', () => {
  let fixture: ComponentFixture<PayrollPeriodFilterBarComponent>;
  let component: PayrollPeriodFilterBarComponent;
  let filterChangeSpy: jasmine.Spy<(v: PayrollPeriodFilterValue) => void>;
  let filterResetSpy: jasmine.Spy<() => void>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        PayrollPeriodFilterBarComponent,
        ReactiveFormsModule,
        NoopAnimationsModule,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PayrollPeriodFilterBarComponent);
    component = fixture.componentInstance;

    filterChangeSpy = spyOn(component.filterChange, 'emit');
    filterResetSpy = spyOn(component.filterReset, 'emit');

    fixture.detectChanges();
  });

  // ==================================================================
  // Behaviour
  // ==================================================================

  describe('buildValue (via onSubmit)', () => {
    it('emits null fields when the form is empty', () => {
      component.onSubmit();

      expect(filterChangeSpy).toHaveBeenCalledOnceWith({
        keyword: null,
        frequency: null,
        isClosed: null,
        startDate: null,
        endDate: null,
      } as PayrollPeriodFilterValue);
    });

    it('trims keyword and turns whitespace-only into null', () => {
      component.form.controls.keyword.setValue('   ');
      component.onSubmit();

      expect(filterChangeSpy.calls.mostRecent().args[0].keyword).toBeNull();

      filterChangeSpy.calls.reset();
      component.form.controls.keyword.setValue('  May 2026  ');
      component.onSubmit();

      expect(filterChangeSpy.calls.mostRecent().args[0].keyword).toBe('May 2026');
    });

    it('maps status to isClosed: ALL→null, OPEN→false, CLOSED→true', () => {
      component.form.controls.status.setValue('ALL');
      component.onSubmit();
      expect(filterChangeSpy.calls.mostRecent().args[0].isClosed).toBeNull();

      component.form.controls.status.setValue('OPEN');
      component.onSubmit();
      expect(filterChangeSpy.calls.mostRecent().args[0].isClosed).toBeFalse();

      component.form.controls.status.setValue('CLOSED');
      component.onSubmit();
      expect(filterChangeSpy.calls.mostRecent().args[0].isClosed).toBeTrue();
    });

    it('passes frequency through when set, null otherwise', () => {
      component.form.controls.frequency.setValue('BI_WEEKLY');
      component.onSubmit();
      expect(filterChangeSpy.calls.mostRecent().args[0].frequency).toBe('BI_WEEKLY');

      component.form.controls.frequency.setValue(null);
      component.onSubmit();
      expect(filterChangeSpy.calls.mostRecent().args[0].frequency).toBeNull();
    });

    it('passes ISO start and end dates through; empty strings become null', () => {
      component.form.controls.startDate.setValue('2026-05-01');
      component.form.controls.endDate.setValue('2026-05-31');
      component.onSubmit();
      let v = filterChangeSpy.calls.mostRecent().args[0];
      expect(v.startDate).toBe('2026-05-01');
      expect(v.endDate).toBe('2026-05-31');

      component.form.controls.startDate.setValue('');
      component.form.controls.endDate.setValue('');
      component.onSubmit();
      v = filterChangeSpy.calls.mostRecent().args[0];
      expect(v.startDate).toBeNull();
      expect(v.endDate).toBeNull();
    });
  });

  describe('onSubmit', () => {
    it('does not emit when canSearch() is false (start > end)', () => {
      component.form.controls.startDate.setValue('2026-05-31');
      component.form.controls.endDate.setValue('2026-05-01');
      component.form.markAllAsTouched();

      component.onSubmit();

      expect(filterChangeSpy).not.toHaveBeenCalled();
      expect(component.form.controls.startDate.touched).toBeTrue();
    });

    it('emits when only one bound is set (open upper/lower bound allowed)', () => {
      component.form.controls.startDate.setValue('2026-05-01');
      component.onSubmit();
      expect(filterChangeSpy).toHaveBeenCalledTimes(1);

      component.form.reset();
      component.form.controls.endDate.setValue('2026-05-31');
      component.onSubmit();
      expect(filterChangeSpy).toHaveBeenCalledTimes(2);
    });

    it('emits when start == end (same day is a valid bound)', () => {
      component.form.controls.startDate.setValue('2026-05-15');
      component.form.controls.endDate.setValue('2026-05-15');

      component.onSubmit();

      expect(filterChangeSpy).toHaveBeenCalledTimes(1);
    });
  });

  describe('onReset', () => {
    it('resets every field to its default', () => {
      component.form.controls.keyword.setValue('May');
      component.form.controls.frequency.setValue('MONTHLY');
      component.form.controls.status.setValue('CLOSED');
      component.form.controls.startDate.setValue('2026-05-01');
      component.form.controls.endDate.setValue('2026-05-31');

      component.onReset();

      expect(component.form.value).toEqual({
        keyword: '',
        frequency: null,
        status: 'ALL',
        startDate: null,
        endDate: null,
      });
    });

    it('emits filterReset and does not emit filterChange', () => {
      component.onReset();
      expect(filterResetSpy).toHaveBeenCalledTimes(1);
      expect(filterChangeSpy).not.toHaveBeenCalled();
    });

    it('clears canSearch side effects by resetting the start/end signals', () => {
      component.form.controls.startDate.setValue('2026-05-31');
      component.form.controls.endDate.setValue('2026-05-01');
      expect(component.canSearch()).toBeFalse();

      component.onReset();

      expect(component.canSearch()).toBeTrue();
    });
  });

  // ==================================================================
  // Template
  // ==================================================================

  describe('template', () => {
    function button(testId: string): HTMLButtonElement {
      return fixture.nativeElement.querySelector(
        `[data-testid="${testId}"]`,
      ) as HTMLButtonElement;
    }

    it('renders the Search button disabled when start > end, enabled otherwise', () => {
      component.form.controls.startDate.setValue('2026-05-31');
      component.form.controls.endDate.setValue('2026-05-01');
      fixture.detectChanges();
      expect(button('filter-search').disabled).toBeTrue();

      component.form.controls.endDate.setValue('2026-05-31');
      fixture.detectChanges();
      expect(button('filter-search').disabled).toBeFalse();
    });

    it('clicking Search emits filterChange', () => {
      button('filter-search').click();
      expect(filterChangeSpy).toHaveBeenCalledTimes(1);
    });

    it('clicking Clear emits filterReset and clears the fields', () => {
      component.form.controls.keyword.setValue('May');
      fixture.detectChanges();

      button('filter-reset').click();
      fixture.detectChanges();

      expect(filterResetSpy).toHaveBeenCalledTimes(1);
      expect(component.form.controls.keyword.value).toBe('');
    });

    it('Enter in the keyword input submits', () => {
      const keyword = fixture.nativeElement.querySelector(
        '[data-testid="filter-keyword"]',
      ) as HTMLInputElement;
      keyword.value = 'May';
      keyword.dispatchEvent(new Event('input'));

      keyword.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }),
      );

      expect(filterChangeSpy).toHaveBeenCalledTimes(1);
    });

    it('renders three status options with the "Closed" label', () => {
      const select = fixture.nativeElement.querySelector(
        '[data-testid="filter-status"]',
      ) as HTMLElement;
      // The select is a Material component; its options are in an overlay.
      // Assert what this test can assert without reaching into the overlay:
      // the field renders and statusOptions covers the three states.
      expect(select).toBeTruthy();
      expect(component.statusOptions.map((o) => o.label)).toEqual([
        'All',
        'Open',
        'Closed',
      ]);
    });
  });
});
