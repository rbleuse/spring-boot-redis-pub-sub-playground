import { Component, Inject, OnDestroy, OnInit, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatListModule } from '@angular/material/list';
import { JobApiService } from '../core/job-api.service';
import { JobStreamService } from '../core/job-stream.service';
import { Job, JobProgressEvent } from '../core/job.models';

@Component({
  selector: 'app-job-detail-dialog',
  standalone: true,
  imports: [MatDialogModule, MatProgressBarModule, MatListModule],
  templateUrl: './job-detail-dialog.html',
})
export class JobDetailDialog implements OnInit, OnDestroy {
  readonly job = signal<Job | undefined>(undefined);
  readonly log = signal<JobProgressEvent[]>([]);
  readonly notFound = signal(false);
  private unsubscribe?: () => void;

  constructor(
    @Inject(MAT_DIALOG_DATA) public readonly jobId: string,
    private readonly api: JobApiService,
    private readonly stream: JobStreamService,
  ) {}

  ngOnInit(): void {
    // Seed from REST (recovers state pub/sub may have missed), then go live.
    this.api.get(this.jobId).subscribe({
      next: j => this.job.set(j),
      error: err => { if (err?.status === 404) { this.notFound.set(true); } },
    });
    this.unsubscribe = this.stream.subscribeJob(this.jobId, event => {
      this.job.set({
        jobId: event.jobId, name: event.name, status: event.status, progress: event.progress,
        workerId: event.workerId, error: event.error,
        submittedAt: this.job()?.submittedAt ?? event.timestamp, updatedAt: event.timestamp,
      });
      this.log.update(l => [...l, event]);
    });
  }

  ngOnDestroy(): void { this.unsubscribe?.(); }
}
