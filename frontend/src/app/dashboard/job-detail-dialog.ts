import { Component, Inject, OnDestroy, OnInit, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatListModule } from '@angular/material/list';
import { JobApiService } from '../core/job-api.service';
import { JobStreamService } from '../core/job-stream.service';
import { Job, JobProgressEvent, mergeEvent, mergeSnapshot } from '../core/job.models';

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
      next: (j) => this.job.update((current) => mergeSnapshot(current, j)),
      error: (err) => {
        if (err?.status === 404) {
          this.notFound.set(true);
        }
      },
    });
    this.unsubscribe = this.stream.subscribeJob(this.jobId, (event) => {
      this.job.set(mergeEvent(this.job(), event));
      this.log.update((l) => [...l, event]);
    });
  }

  ngOnDestroy(): void {
    this.unsubscribe?.();
  }
}
