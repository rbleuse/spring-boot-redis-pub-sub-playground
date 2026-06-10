import { TestBed } from '@angular/core/testing';
import { JobsTable } from './jobs-table';
import { JobStore } from '../core/job-store';
import { Job } from '../core/job.models';

describe('JobsTable', () => {
  it('renders a row per job from the store', () => {
    const store = new JobStore();
    const job: Job = {
      jobId: 'j1', name: 'resize', status: 'RUNNING', progress: 40,
      submittedAt: '2026-06-10T10:00:00Z', updatedAt: '2026-06-10T10:00:05Z', workerId: 'app-1',
    };
    store.seed([job]);
    TestBed.configureTestingModule({
      providers: [{ provide: JobStore, useValue: store }],
    });
    const fixture = TestBed.createComponent(JobsTable);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('resize');
    expect(text).toContain('RUNNING');
  });
});
