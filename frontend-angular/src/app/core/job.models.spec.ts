import { mergeEvent, mergeSnapshot, Job, JobProgressEvent } from './job.models';

describe('mergeEvent', () => {
  const base: Job = {
    jobId: 'j1',
    name: 'resize',
    status: 'QUEUED',
    progress: 0,
    submittedAt: '2026-06-10T10:00:00Z',
    updatedAt: '2026-06-10T10:00:00Z',
  };
  const event: JobProgressEvent = {
    jobId: 'j1',
    name: 'resize',
    status: 'RUNNING',
    progress: 40,
    workerId: 'app-3f9c',
    timestamp: '2026-06-10T10:00:05Z',
  };

  it('updates an existing job from an event, preserving submittedAt', () => {
    const merged = mergeEvent(base, event);
    expect(merged.status).toBe('RUNNING');
    expect(merged.progress).toBe(40);
    expect(merged.workerId).toBe('app-3f9c');
    expect(merged.updatedAt).toBe('2026-06-10T10:00:05Z');
    expect(merged.submittedAt).toBe('2026-06-10T10:00:00Z');
  });

  it('creates a job from an event when no prior job exists, using timestamp as submittedAt', () => {
    const created = mergeEvent(undefined, event);
    expect(created.jobId).toBe('j1');
    expect(created.submittedAt).toBe('2026-06-10T10:00:05Z');
    expect(created.status).toBe('RUNNING');
  });

  it('keeps a newer live job when an older REST snapshot arrives', () => {
    const live = mergeEvent(base, event);
    const merged = mergeSnapshot(live, base);
    expect(merged.status).toBe('RUNNING');
    expect(merged.updatedAt).toBe(event.timestamp);
  });

  it('uses a REST snapshot when it is at least as recent as live state', () => {
    const snapshot: Job = {
      ...base,
      status: 'COMPLETED',
      progress: 100,
      updatedAt: event.timestamp,
    };
    const merged = mergeSnapshot(mergeEvent(base, event), snapshot);
    expect(merged).toBe(snapshot);
  });

  it('compares timestamps chronologically when fractional precision differs', () => {
    const live: Job = {
      ...base,
      status: 'RUNNING',
      updatedAt: '2026-06-10T10:00:00.100Z',
    };
    const merged = mergeSnapshot(live, base);
    expect(merged).toBe(live);
  });
});
