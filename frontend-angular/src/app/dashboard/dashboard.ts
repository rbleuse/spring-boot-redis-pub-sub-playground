import { Component, OnInit, inject } from '@angular/core';
import { SubmitForm } from './submit-form';
import { JobsTable } from './jobs-table';
import { JobApiService } from '../core/job-api.service';
import { JobStore } from '../core/job-store';
import { ConnectionStatus, JobStreamService } from '../core/job-stream.service';

const BADGE =
  'group/badge inline-flex h-5 w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-4xl border px-2 py-0.5 text-xs font-medium whitespace-nowrap transition-all focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 [&>svg]:pointer-events-none [&>svg]:size-3!';

const CONN_BADGE: Record<ConnectionStatus, string> = {
  connected: `${BADGE} border-transparent bg-green-600 text-white`,
  reconnecting: `${BADGE} border-transparent bg-amber-500 text-white`,
  closed: `${BADGE} border-transparent bg-secondary text-secondary-foreground`,
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [SubmitForm, JobsTable],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit {
  private readonly api = inject(JobApiService);
  private readonly store = inject(JobStore);
  private readonly stream = inject(JobStreamService);
  readonly status = this.stream.status;

  protected connClass(status: ConnectionStatus): string {
    return `capitalize ${CONN_BADGE[status]}`;
  }

  ngOnInit(): void {
    this.api.list().subscribe((jobs) => this.store.seed(jobs)); // REST seed first
    this.stream.connect(); // then go live
  }
}
