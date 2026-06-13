import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { JobApiService } from '../core/job-api.service';
import { ButtonDirective } from '../ui/button';
import { CardContentDirective, CardDirective, CardHeaderDirective, CardTitleDirective } from '../ui/card';
import { InputDirective } from '../ui/input';
import { LabelDirective } from '../ui/label';

@Component({
  selector: 'app-submit-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    CardDirective,
    CardHeaderDirective,
    CardTitleDirective,
    CardContentDirective,
    InputDirective,
    LabelDirective,
  ],
  templateUrl: './submit-form.html',
})
export class SubmitForm {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(JobApiService);
  readonly notice = signal('');

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    durationMs: [10000, [Validators.required, Validators.min(1000), Validators.max(120000)]],
    failureRate: [0, [Validators.required, Validators.min(0), Validators.max(1)]],
    scheduledAt: [''], // datetime-local string; empty = run now
  });

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    const { scheduledAt, ...rest } = this.form.getRawValue();
    this.api
      .submit({ ...rest, scheduledAt: scheduledAt ? new Date(scheduledAt).toISOString() : undefined })
      .subscribe({
        next: (r) => {
          this.notice.set(`Submitted ${r.jobId}`);
          this.form.reset({ name: '', durationMs: 10000, failureRate: 0, scheduledAt: '' });
        },
        error: (e) => this.notice.set(e?.error?.detail ?? 'Submit failed'),
      });
  }
}
