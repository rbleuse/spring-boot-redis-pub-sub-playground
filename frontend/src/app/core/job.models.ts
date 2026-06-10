export type JobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface Job {
  jobId: string;
  name: string;
  status: JobStatus;
  progress: number;
  submittedAt: string;
  updatedAt: string;
  workerId?: string;
  error?: string;
}

export interface JobProgressEvent {
  jobId: string;
  name: string;
  status: JobStatus;
  progress: number;
  workerId?: string;
  error?: string;
  timestamp: string;
}

export interface SubmitJobRequest {
  name: string;
  durationMs?: number;
  failureRate?: number;
}

export interface SubmitJobResponse {
  jobId: string;
}

export function isTerminal(status: JobStatus): boolean {
  return status === 'COMPLETED' || status === 'FAILED';
}

/** Merge a live event into a job. When `prior` is undefined, create one (event seen before REST seed). */
export function mergeEvent(prior: Job | undefined, event: JobProgressEvent): Job {
  return {
    jobId: event.jobId,
    name: event.name,
    status: event.status,
    progress: event.progress,
    workerId: event.workerId,
    error: event.error,
    submittedAt: prior?.submittedAt ?? event.timestamp,
    updatedAt: event.timestamp,
  };
}
