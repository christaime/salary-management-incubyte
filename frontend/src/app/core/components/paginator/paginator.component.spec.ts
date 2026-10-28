import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { PaginatorComponent } from './paginator.component';

describe('PaginatorComponent', () => {
  let fixture: ComponentFixture<PaginatorComponent>;
  let component: PaginatorComponent;

  function createWith(overrides: {
    totalElements?: number;
    page?: number;
    size?: number;
    unitLabel?: string;
  } = {}): void {
    fixture = TestBed.createComponent(PaginatorComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('totalElements', overrides.totalElements ?? 100);
    fixture.componentRef.setInput('page', overrides.page ?? 0);
    fixture.componentRef.setInput('size', overrides.size ?? 10);
    fixture.componentRef.setInput('unitLabel', overrides.unitLabel ?? 'items');
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PaginatorComponent, NoopAnimationsModule],
    });
  });

  // ------------------------------------------------------------------
  // Range
  // ------------------------------------------------------------------

  it('computes the range for the first page', () => {
    createWith({ totalElements: 100, page: 0, size: 10 });
    expect(component.range()).toEqual([1, 10]);
  });

  it('computes the range for a middle page', () => {
    createWith({ totalElements: 100, page: 3, size: 10 });
    expect(component.range()).toEqual([31, 40]);
  });

  it('caps the range end at the total when the last page is partial', () => {
    createWith({ totalElements: 47, page: 4, size: 10 });
    expect(component.range()).toEqual([41, 47]);
  });

  it('returns [0, 0] when the dataset is empty', () => {
    createWith({ totalElements: 0, page: 0, size: 10 });
    expect(component.range()).toEqual([0, 0]);
  });

  // ------------------------------------------------------------------
  // Total pages
  // ------------------------------------------------------------------

  it('computes total pages when the total is a multiple of the size', () => {
    createWith({ totalElements: 100, page: 0, size: 10 });
    expect(component.totalPages()).toBe(10);
  });

  it('computes total pages when the last page is partial', () => {
    createWith({ totalElements: 47, page: 0, size: 10 });
    expect(component.totalPages()).toBe(5);
  });

  it('returns 0 total pages for an empty dataset', () => {
    createWith({ totalElements: 0, page: 0, size: 10 });
    expect(component.totalPages()).toBe(0);
  });

  // ------------------------------------------------------------------
  // Rendering
  // ------------------------------------------------------------------

  it('renders the summary line with range, total, and unit label', () => {
    createWith({ totalElements: 100, page: 0, size: 10, unitLabel: 'rows' });
    const el = fixture.nativeElement as HTMLElement;
    const summary = el.querySelector('[data-testid="paginator-range"]')?.textContent ?? '';

    expect(summary).toContain('1–10');
    expect(summary).toContain('100');
    expect(summary).toContain('rows');
  });

  it('renders the current page and total pages', () => {
    createWith({ totalElements: 100, page: 3, size: 10 });
    const el = fixture.nativeElement as HTMLElement;
    const text = el.textContent ?? '';

    expect(text).toContain('Page');
    expect(text).toContain('4');
    expect(text).toContain('10');
  });

  it('renders the material paginator', () => {
    createWith();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('mat-paginator')).not.toBeNull();
  });

  // ------------------------------------------------------------------
  // Events
  // ------------------------------------------------------------------

  it('emits pageChange when the page event fires', () => {
    createWith({ totalElements: 100, page: 0, size: 10 });

    const spy = jasmine.createSpy('pageChange');
    component.pageChange.subscribe(spy);

    component.onPageEvent({
      pageIndex: 2,
      pageSize: 25,
      length: 100,
      previousPageIndex: 0,
    });

    expect(spy).toHaveBeenCalledOnceWith({ page: 2, size: 25 });
  });

  it('forwards the page size when the user changes it', () => {
    createWith({ totalElements: 100, page: 0, size: 10 });

    const spy = jasmine.createSpy('pageChange');
    component.pageChange.subscribe(spy);

    component.onPageEvent({
      pageIndex: 0,
      pageSize: 50,
      length: 100,
      previousPageIndex: 0,
    });

    expect(spy).toHaveBeenCalledOnceWith({ page: 0, size: 50 });
  });

  // ------------------------------------------------------------------
  // Formatting
  // ------------------------------------------------------------------

   it('formats numbers with thousand separators', () => {
     createWith();
     expect(component.formatNumber(10000)).toBe('10,000');
     expect(component.formatNumber(0)).toBe('0');
     expect(component.formatNumber(999)).toBe('999');
   });
});
