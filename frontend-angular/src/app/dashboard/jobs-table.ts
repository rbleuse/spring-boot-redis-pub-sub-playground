import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { JobApiService } from '../core/job-api.service';
import { JobStore } from '../core/job-store';
import { Job, JobStatus, isTerminal } from '../core/job.models';
import { BadgeDirective } from '../ui/badge';
import { ButtonDirective } from '../ui/button';
import { CardContentDirective, CardDirective, CardHeaderDirective, CardTitleDirective } from '../ui/card';
import { Progress } from '../ui/progress';
import {
  TableBodyDirective,
  TableCellDirective,
  TableDirective,
  TableHeadDirective,
  TableHeaderDirective,
  TableRowDirective,
} from '../ui/table';

type BadgeVariant = 'default' | 'secondary' | 'destructive' | 'outline';

// ponytail: status → badge styling copied from frontend-react/src/App.tsx
const STATUS_BADGE: Record<JobStatus, { variant: BadgeVariant; class: string }> = {
  SCHEDULED: { variant: 'outline', class: 'text-purple-600 border-purple-300' },
  QUEUED: { variant: 'secondary', class: '' },
  RUNNING: { variant: 'default', class: 'bg-blue-600 text-white' },
  COMPLETED: { variant: 'default', class: 'bg-green-600 text-white' },
  FAILED: { variant: 'destructive', class: '' },
  CANCELLED: { variant: 'outline', class: 'text-muted-foreground' },
};

@Component({
  selector: 'app-jobs-table',
  standalone: true,
  imports: [
    DatePipe,
    BadgeDirective,
    ButtonDirective,
    Progress,
    CardDirective,
    CardHeaderDirective,
    CardTitleDirective,
    CardContentDirective,
    TableDirective,
    TableHeaderDirective,
    TableBodyDirective,
    TableRowDirective,
    TableHeadDirective,
    TableCellDirective,
  ],
  templateUrl: './jobs-table.html',
})
export class JobsTable {
  private readonly store = inject(JobStore);
  private readonly api = inject(JobApiService);
  readonly jobs = this.store.jobs;
  readonly notice = signal('');

  protected readonly isTerminal = isTerminal;
  protected badge(status: JobStatus) {
    return STATUS_BADGE[status];
  }

  cancel(job: Job): void {
    this.api.cancel(job.jobId).subscribe({
      error: (e) => this.notice.set(e?.error?.detail ?? 'Cancel failed'),
    });
  }
}
