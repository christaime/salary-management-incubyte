import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ToastService } from '../ui/toast/toast.service';
import { ApiError } from './api-error';
import { httpErrorInterceptor } from './http-error.interceptor';

describe('httpErrorInterceptor', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let toast: jasmine.SpyObj<ToastService>;

  const url = '/api/v1/reports/aggregate';

  beforeEach(() => {
    toast = jasmine.createSpyObj<ToastService>('ToastService', [
      'success',
      'error',
      'info',
      'warning',
      'dismiss',
    ]);

    TestBed.configureTestingModule({
      providers: [
        { provide: ToastService, useValue: toast },
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
  });

  afterEach(() => http.verify());

  it('maps a 400 with VALIDATION_FAILED into ApiError and rethrows', (done) => {
    client.post(url, {}).subscribe({
      error: (err: ApiError) => {
        expect(err.code).toBe('VALIDATION_FAILED');
        expect(err.status).toBe(400);
        expect(err.fieldErrors.length).toBe(1);
        expect(err.fieldErrors[0].field).toBe('dateRange');
        expect(toast.error).not.toHaveBeenCalled();
        done();
      },
    });

    const req = http.expectOne(url);
    req.flush(
      {
        code: 'VALIDATION_FAILED',
        message: 'Validation failed',
        errors: [
          { field: 'dateRange', code: 'INVALID', message: 'end before start' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
  });

  it('toasts on a 500 with no parseable body', (done) => {
    client.post(url, {}).subscribe({
      error: (err: ApiError) => {
        expect(err.code).toBe('SERVER_ERROR');
        expect(toast.error).toHaveBeenCalledOnceWith(
          jasmine.stringMatching(/server encountered an error/i),
        );
        done();
      },
    });

    const req = http.expectOne(url);
    req.flush('boom', { status: 500, statusText: 'Server Error' });
  });

  it('toasts on network failure (status 0)', (done) => {
    client.post(url, {}).subscribe({
      error: (err: ApiError) => {
        expect(err.code).toBe('NETWORK_ERROR');
        expect(toast.error).toHaveBeenCalled();
        done();
      },
    });

    const req = http.expectOne(url);
    req.error(new ProgressEvent('error'));
  });
});
