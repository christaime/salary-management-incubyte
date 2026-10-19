import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ToastService);
  });

  it('pushes an error toast and dismisses by id', () => {
    service.error('boom');
    expect(service.toasts().length).toBe(1);
    const id = service.toasts()[0].id;
    service.dismiss(id);
    expect(service.toasts().length).toBe(0);
  });

  it('auto-dismisses after duration', () => {
    jasmine.clock().install();
    try {
      service.info('hi', 1000);
      expect(service.toasts().length).toBe(1);
      jasmine.clock().tick(1001);
      expect(service.toasts().length).toBe(0);
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('does not auto-dismiss when duration is 0', () => {
    jasmine.clock().install();
    try {
      service.warning('sticky', 0);
      jasmine.clock().tick(10_000);
      expect(service.toasts().length).toBe(1);
    } finally {
      jasmine.clock().uninstall();
    }
  });
});
