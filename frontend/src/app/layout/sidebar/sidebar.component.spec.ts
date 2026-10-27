import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { SidebarComponent } from './sidebar.component';
import { SidebarService } from '../sidebar.service';

describe('SidebarComponent', () => {
  let fixture: ComponentFixture<SidebarComponent>;
  let service: SidebarService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [provideRouter([]), SidebarService],
    }).compileComponents();

    service = TestBed.inject(SidebarService);
    fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
  });

  function rerender(): void {
    fixture.detectChanges();
  }

  // ------------------------------------------------------------------
  // Visibility
  // ------------------------------------------------------------------

  it('renders the sidebar when isOpen() is true', () => {
    service.isOpen.set(true);
    rerender();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="app-sidebar"]')).not.toBeNull();
  });

  it('does not render the sidebar when isOpen() is false', () => {
    service.isOpen.set(false);
    rerender();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="app-sidebar"]')).toBeNull();
  });

  // ------------------------------------------------------------------
  // Nav items
  // ------------------------------------------------------------------

  it('renders 5 nav items', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.nav-item').length).toBe(5);
  });

  it('exposes the employee, aggregate, and earnings ranking nav items', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="nav-employees"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="nav-aggregate"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="nav-earnings-ranking"]')).not.toBeNull();
  });

  // ------------------------------------------------------------------
  // Collapsed state
  // ------------------------------------------------------------------

  it('renders nav labels and the sidebar title when not collapsed', () => {
    service.isCollapsed.set(false);
    rerender();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelectorAll('.nav-label').length).toBe(5);
    expect(el.querySelector('[data-testid="sidebar-title"]')?.textContent)
      .toContain('Navigation');
  });

  it('hides nav labels and the sidebar title when collapsed', () => {
    service.isCollapsed.set(true);
    rerender();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelectorAll('.nav-label').length).toBe(0);
    expect(el.querySelector('[data-testid="sidebar-title"]')).toBeNull();
  });

  it('applies the "collapsed" class to the aside when collapsed', () => {
    service.isCollapsed.set(true);
    rerender();
    const aside = (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLElement>('[data-testid="app-sidebar"]')!;
    expect(aside.classList.contains('collapsed')).toBeTrue();
  });

  it('does not apply the "collapsed" class when expanded', () => {
    service.isCollapsed.set(false);
    rerender();
    const aside = (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLElement>('[data-testid="app-sidebar"]')!;
    expect(aside.classList.contains('collapsed')).toBeFalse();
  });

  // ------------------------------------------------------------------
  // Interactions
  // ------------------------------------------------------------------

  it('calls toggleCollapse when the collapse button is clicked', () => {
    spyOn(service, 'toggleCollapse').and.callThrough();
    const btn = (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[data-testid="sidebar-collapse-button"]')!;
    expect(btn).not.toBeNull();

    btn.click();

    expect(service.toggleCollapse).toHaveBeenCalledTimes(1);
  });
});
