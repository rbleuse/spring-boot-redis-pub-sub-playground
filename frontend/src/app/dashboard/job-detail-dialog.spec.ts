import '@angular/compiler';
import { Subject } from 'rxjs';
import { JobApiService } from '../core/job-api.service';
import { JobStreamService } from '../core/job-stream.service';
import { Job, JobProgressEvent } from '../core/job.models';
import { JobDetailDialog } from './job-detail-dialog';

describe('JobDetailDialog', () => {
  it('does not overwrite a newer live event with an older REST response', () => {
    const response = new Subject<Job>();
    let onEvent: ((event: JobProgressEvent) => void) | undefined;
    const api = {
      get: () => response.asObservable(),
    } as unknown as JobApiService;
    const stream = {
      subscribeJob: (_jobId: string, handler: (event: JobProgressEvent) => void) => {
        onEvent = handler;
        return () => {};
      },
    } as unknown as JobStreamService;
    const dialog = new JobDetailDialog('j1', api, stream);

    dialog.ngOnInit();
    onEvent?.({
      jobId: 'j1',
      name: 'resize',
      status: 'RUNNING',
      progress: 40,
      timestamp: '2026-06-10T10:00:05Z',
    });
    response.next({
      jobId: 'j1',
      name: 'resize',
      status: 'QUEUED',
      progress: 0,
      submittedAt: '2026-06-10T10:00:00Z',
      updatedAt: '2026-06-10T10:00:00Z',
    });

    expect(dialog.job()?.status).toBe('RUNNING');
    expect(dialog.job()?.progress).toBe(40);
  });
});
