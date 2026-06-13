import { Component, OnInit, inject } from '@angular/core';
import { SubmitForm } from './submit-form';
import { JobsTable } from './jobs-table';
import { JobApiService } from '../core/job-api.service';
import { JobStore } from '../core/job-store';
import { ConnectionStatus, JobStreamService } from '../core/job-stream.service';
import { BadgeDirective } from '../ui/badge';

// ponytail: connection status → badge class, copied from frontend-react/src/App.tsx
const CONN_BADGE: Record<ConnectionStatus, string> = {
  connected: 'bg-green-600 text-white',
  reconnecting: 'bg-amber-500 text-white',
  closed: '',
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [SubmitForm, JobsTable, BadgeDirective],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit {
  private readonly api = inject(JobApiService);
  private readonly store = inject(JobStore);
  private readonly stream = inject(JobStreamService);
  readonly status = this.stream.status;

  protected connClass(status: ConnectionStatus): string {
    return CONN_BADGE[status];
  }

  ngOnInit(): void {
    this.api.list().subscribe((jobs) => this.store.seed(jobs)); // REST seed first
    this.stream.connect(); // then go live
  }
}
