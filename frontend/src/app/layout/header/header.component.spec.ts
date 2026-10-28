import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HeaderComponent } from './header.component';
import { SidebarService } from '../sidebar.service';

describe('HeaderComponent', () => {
  let fixture: ComponentFixture<HeaderComponent>;
  let sidebarService: jasmine.SpyObj<SidebarService>;

  beforeEach(() => {
    sidebarService = jasmine.createSpyObj('SidebarService', ['toggleOpen', 'toggleCollapse']);

    TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [{ provide: SidebarService, useValue: sidebarService }],
    });

    fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the brand title', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="brand-title"]')?.textContent)
      .toContain('Salary Management');
  });

  it('should call toggleOpen when the menu button is clicked', () => {
    const btn = (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[data-testid="sidebar-toggle-button"]')!;

    btn.click();

    expect(sidebarService.toggleOpen).toHaveBeenCalledTimes(1);
  });
});
