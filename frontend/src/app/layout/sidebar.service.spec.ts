import { TestBed } from '@angular/core/testing';
import { SidebarService } from './sidebar.service';

describe('SidebarService', () => {
  let service: SidebarService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SidebarService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should default isOpen to true', () => {
    expect(service.isOpen()).toBeTrue();
  });

  it('should default isCollapsed to false', () => {
    expect(service.isCollapsed()).toBeFalse();
  });

  it('toggleOpen should flip isOpen', () => {
    service.toggleOpen();
    expect(service.isOpen()).toBeFalse();
    service.toggleOpen();
    expect(service.isOpen()).toBeTrue();
  });

  it('toggleCollapse should flip isCollapsed', () => {
    service.toggleCollapse();
    expect(service.isCollapsed()).toBeTrue();
    service.toggleCollapse();
    expect(service.isCollapsed()).toBeFalse();
  });
});
