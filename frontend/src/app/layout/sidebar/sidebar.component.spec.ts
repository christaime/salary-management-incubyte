import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SidebarComponent } from './sidebar.component';
import { SidebarService } from '../sidebar.service';

describe('SidebarComponent', () => {
  let fixture: ComponentFixture<SidebarComponent>;
  let service: SidebarService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [provideRouter([]), SidebarService],
    });

    service = TestBed.inject(SidebarService);
    fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should not render the sidebar when isOpen() is false', () => {
    service.isOpen.set(false);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="app-sidebar"]')).toBeNull();
  });

  it('should render the sidebar when isOpen() is true', () => {
    service.isOpen.set(true);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="app-sidebar"]')).not.toBeNull();
  });

  it('should render 4 nav items', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.nav-item').length).toBe(4);
  });

  it('should render nav labels when not collapsed', () => {
    service.isCollapsed.set(false);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.nav-label').length).toBe(4);
    expect(el.querySelector('[data-testid="sidebar-title"]')?.textContent).toContain('Navigation');
  });

  it('should hide nav labels when collapsed', () => {
    service.isCollapsed.set(true);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.nav-label').length).toBe(0);
    expect(el.querySelector('[data-testid="sidebar-title"]')).toBeNull();
  });

  it('should call toggleCollapse on the collapse button', () => {
    spyOn(service, 'toggleCollapse').and.callThrough();
    const btn = (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[data-testid="sidebar-collapse-button"]')!;

    btn.click();

    expect(service.toggleCollapse).toHaveBeenCalledTimes(1);
  });

  it('should apply the "collapsed" class when collapsed', () => {
    service.isCollapsed.set(true);
    fixture.detectChanges();
    const aside = (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLElement>('[data-testid="app-sidebar"]')!;
    expect(aside.classList.contains('collapsed')).toBeTrue();
  });

  it('should expose the aggregate and earnings ranking nav items', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="nav-aggregate"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="nav-earnings-ranking"]')).not.toBeNull();
  });
});
