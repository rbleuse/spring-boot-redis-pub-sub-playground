import { JobStore } from './job-store';
import { Job, JobProgressEvent } from './job.models';

describe('JobStore', () => {
  let store: JobStore;
  beforeEach(() => { store = new JobStore(); });

  const job: Job = {
    jobId: 'j1', name: 'resize', status: 'QUEUED', progress: 0,
    submittedAt: '2026-06-10T10:00:00Z', updatedAt: '2026-06-10T10:00:00Z',
  };

  it('seeds jobs from a REST list, newest-updated first', () => {
    const older: Job = { ...job, jobId: 'j0', updatedAt: '2026-06-10T09:00:00Z' };
    store.seed([job, older]);
    expect(store.jobs().map(j => j.jobId)).toEqual(['j1', 'j0']);
  });

  it('applies an event to an existing job', () => {
    store.seed([job]);
    const event: JobProgressEvent = {
      jobId: 'j1', name: 'resize', status: 'RUNNING', progress: 40,
      workerId: 'app-3f9c', timestamp: '2026-06-10T10:00:05Z',
    };
    store.apply(event);
    expect(store.job('j1')()?.status).toBe('RUNNING');
    expect(store.job('j1')()?.progress).toBe(40);
  });

  it('adds a new job when an event arrives for an unknown id', () => {
    const event: JobProgressEvent = {
      jobId: 'jX', name: 'etl', status: 'QUEUED', progress: 0,
      timestamp: '2026-06-10T10:01:00Z',
    };
    store.apply(event);
    expect(store.jobs().length).toBe(1);
    expect(store.job('jX')()?.name).toBe('etl');
  });
});
