import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { httpErrorInterceptor } from './http-error.interceptor';
import { ApiError } from './api-error';

describe('httpErrorInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should pass through backend structured error responses', (done) => {
    const errorBody = { code: 'CUSTOM_ERROR', message: 'Test error', severity: 'ERROR' };

    httpClient.get('/test-api').subscribe({
      next: () => fail('should have failed'),
      error: (err: ApiError) => {
        expect(err.code).toEqual(errorBody.code);
        expect(err.message).toEqual(errorBody.message);
        expect(err.severity).toEqual(errorBody.severity);
        done();
      },
    });

    const req = httpMock.expectOne('/test-api');
    req.flush(errorBody, { status: 400, statusText: 'Bad Request' });
  });

 it('should be a NETWORK_OR_SERVER_ERROR error if no structure error from the backend', (done) => {

    httpClient.get('/test-api').subscribe({
      next: () => fail('should have failed'),
      error: (err: ApiError) => {
        expect(err.code).toEqual('NETWORK_OR_SERVER_ERROR');
        done();
      },
    });

    const req = httpMock.expectOne('/test-api');
    req.flush("error occured!", { status: 400, statusText: 'Bad Request' });
  });
});
