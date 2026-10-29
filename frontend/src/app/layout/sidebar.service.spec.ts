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
    expect(service.isOpen()).toBeFalse();
  });

  it('should default isCollapsed to false', () => {
    expect(service.isCollapsed()).toBeTrue();
  });

  it('toggleOpen should flip isOpen', () => {
    service.toggleOpen();
    expect(service.isOpen()).toBeTrue();
    service.toggleOpen();
    expect(service.isOpen()).toBeFalse();
  });

  it('toggleCollapse should flip isCollapsed', () => {
    service.toggleCollapse();
    expect(service.isCollapsed()).toBeFalse();
    service.toggleCollapse();
    expect(service.isCollapsed()).toBeTrue();
  });
});
