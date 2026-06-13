import { Injectable, signal, computed, Signal } from '@angular/core';
import { Job, JobProgressEvent, mergeEvent, mergeSnapshot } from './job.models';

@Injectable({ providedIn: 'root' })
export class JobStore {
  private readonly byId = signal<Map<string, Job>>(new Map());

  /** All jobs, most-recently-updated first. */
  readonly jobs = computed(() =>
    [...this.byId().values()].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)),
  );

  /** Reactive accessor for one job. */
  job(jobId: string): Signal<Job | undefined> {
    return computed(() => this.byId().get(jobId));
  }

  /** Merge REST-loaded snapshots without overwriting newer live events. */
  seed(jobs: Job[]): void {
    const next = new Map(this.byId());
    for (const job of jobs) {
      next.set(job.jobId, mergeSnapshot(next.get(job.jobId), job));
    }
    this.byId.set(next);
  }

  /** Merge a single live event. */
  apply(event: JobProgressEvent): void {
    const next = new Map(this.byId());
    next.set(event.jobId, mergeEvent(next.get(event.jobId), event));
    this.byId.set(next);
  }
}
