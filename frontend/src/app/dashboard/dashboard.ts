import { Component, OnInit, inject } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { SubmitForm } from './submit-form';
import { JobsTable } from './jobs-table';
import { JobDetailDialog } from './job-detail-dialog';
import { JobApiService } from '../core/job-api.service';
import { JobStore } from '../core/job-store';
import { JobStreamService } from '../core/job-stream.service';
import { Job } from '../core/job.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [MatToolbarModule, MatIconModule, SubmitForm, JobsTable],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly api = inject(JobApiService);
  private readonly store = inject(JobStore);
  private readonly stream = inject(JobStreamService);
  private readonly dialog = inject(MatDialog);
  readonly status = this.stream.status;

  ngOnInit(): void {
    this.api.list().subscribe(jobs => this.store.seed(jobs));  // REST seed first
    this.stream.connect();                                      // then go live
  }

  openJob(job: Job): void {
    this.dialog.open(JobDetailDialog, { data: job.jobId, width: '560px' });
  }
}
