import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Job, SubmitJobRequest, SubmitJobResponse } from './job.models';

@Injectable({ providedIn: 'root' })
export class JobApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/jobs`;

  submit(req: SubmitJobRequest): Observable<SubmitJobResponse> {
    return this.http.post<SubmitJobResponse>(this.base, req);
  }

  list(): Observable<Job[]> {
    return this.http.get<Job[]>(this.base);
  }

  get(jobId: string): Observable<Job> {
    return this.http.get<Job>(`${this.base}/${jobId}`);
  }
}
