import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { JobApiService } from '../core/job-api.service';
import { JobStore } from '../core/job-store';
import { Job, JobStatus, isTerminal } from '../core/job.models';

const BADGE =
  'group/badge inline-flex h-5 w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-4xl border px-2 py-0.5 text-xs font-medium whitespace-nowrap transition-all focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 [&>svg]:pointer-events-none [&>svg]:size-3!';

const STATUS_BADGE: Record<JobStatus, string> = {
  SCHEDULED: `${BADGE} border-purple-300 text-purple-600`,
  QUEUED: `${BADGE} border-transparent bg-secondary text-secondary-foreground`,
  RUNNING: `${BADGE} border-transparent bg-blue-600 text-white`,
  COMPLETED: `${BADGE} border-transparent bg-green-600 text-white`,
  FAILED: `${BADGE} border-transparent bg-destructive/10 text-destructive focus-visible:ring-destructive/20`,
  CANCELLED: `${BADGE} border-border text-muted-foreground`,
};

@Component({
  selector: 'app-jobs-table',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './jobs-table.html',
})
export class JobsTable {
  private readonly store = inject(JobStore);
  private readonly api = inject(JobApiService);
  readonly jobs = this.store.jobs;
  readonly notice = signal('');

  protected readonly isTerminal = isTerminal;
  protected badgeClass(status: JobStatus): string {
    return STATUS_BADGE[status];
  }

  cancel(job: Job): void {
    this.api.cancel(job.jobId).subscribe({
      error: (e) => this.notice.set(e?.error?.detail ?? 'Cancel failed'),
    });
  }
}
