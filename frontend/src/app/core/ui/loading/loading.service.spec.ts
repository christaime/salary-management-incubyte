import { TestBed } from '@angular/core/testing';
import { LoadingService } from './loading.service';

describe('LoadingService', () => {
  let service: LoadingService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(LoadingService);
  });

  it('isLoading is false initially', () => {
    expect(service.isLoading()).toBeFalse();
  });

  it('toggles with begin/end and ignores unbalanced end', () => {
    service.begin('x');
    expect(service.isLoading()).toBeTrue();
    expect(service.isLoadingFor('x')).toBeTrue();
    service.end('x');
    expect(service.isLoading()).toBeFalse();
    service.end('x'); // unbalanced — must not go negative
    expect(service.isLoading()).toBeFalse();
  });

  it('tracks multiple keys independently', () => {
    service.begin('a');
    service.begin('b');
    expect(service.isLoadingFor('a')).toBeTrue();
    expect(service.isLoadingFor('b')).toBeTrue();
    service.end('a');
    expect(service.isLoadingFor('a')).toBeFalse();
    expect(service.isLoadingFor('b')).toBeTrue();
    expect(service.isLoading()).toBeTrue();
  });
});
