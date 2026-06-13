export type JobStatus = 'SCHEDULED' | 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface Job {
  jobId: string;
  name: string;
  status: JobStatus;
  progress: number;
  submittedAt: string;
  updatedAt: string;
  scheduledAt?: string;
  workerId?: string;
  error?: string;
}

export interface JobProgressEvent {
  jobId: string;
  name: string;
  status: JobStatus;
  progress: number;
  scheduledAt?: string;
  workerId?: string;
  error?: string;
  timestamp: string;
}

export interface SubmitJobRequest {
  name: string;
  durationMs?: number;
  failureRate?: number;
  scheduledAt?: string; // ISO-8601 instant; omit to run now
}

export interface SubmitJobResponse {
  jobId: string;
}

export function isTerminal(status: JobStatus): boolean {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED';
}

/** Merge a live event into a job. When `prior` is undefined, create one (event seen before REST seed). */
export function mergeEvent(prior: Job | undefined, event: JobProgressEvent): Job {
  return {
    jobId: event.jobId,
    name: event.name,
    status: event.status,
    progress: event.progress,
    scheduledAt: event.scheduledAt ?? prior?.scheduledAt,
    workerId: event.workerId,
    error: event.error,
    submittedAt: prior?.submittedAt ?? event.timestamp,
    updatedAt: event.timestamp,
  };
}

/** Keep whichever representation has the latest update. Prefer snapshots on equal timestamps. */
export function mergeSnapshot(prior: Job | undefined, snapshot: Job): Job {
  return prior && Date.parse(prior.updatedAt) > Date.parse(snapshot.updatedAt) ? prior : snapshot;
}
