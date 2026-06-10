import { Component, inject, output } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { JobStore } from '../core/job-store';
import { Job } from '../core/job.models';

@Component({
  selector: 'app-jobs-table',
  standalone: true,
  imports: [MatTableModule, MatProgressBarModule],
  templateUrl: './jobs-table.html',
})
export class JobsTable {
  private readonly store = inject(JobStore);
  readonly jobs = this.store.jobs;
  readonly columns = ['name', 'status', 'progress', 'workerId'];
  readonly rowClick = output<Job>();
}
