import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { JobApiService } from './job-api.service';
import { environment } from '../../environments/environment';

describe('JobApiService', () => {
  let api: JobApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(JobApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('POSTs a submit request and returns the job id', () => {
    let result: string | undefined;
    api.submit({ name: 'resize', durationMs: 5000, failureRate: 0 })
       .subscribe(r => (result = r.jobId));
    const req = http.expectOne(`${environment.apiBaseUrl}/jobs`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.name).toBe('resize');
    req.flush({ jobId: 'j1' });
    expect(result).toBe('j1');
  });

  it('GETs the job list', () => {
    api.list().subscribe();
    const req = http.expectOne(`${environment.apiBaseUrl}/jobs`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('GETs one job by id', () => {
    api.get('j1').subscribe();
    const req = http.expectOne(`${environment.apiBaseUrl}/jobs/j1`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});
