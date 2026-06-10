import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { JobApiService } from '../core/job-api.service';

@Component({
  selector: 'app-submit-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './submit-form.html',
})
export class SubmitForm {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(JobApiService);
  private readonly snack = inject(MatSnackBar);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    durationMs: [10000, [Validators.required, Validators.min(1000), Validators.max(120000)]],
    failureRate: [0, [Validators.required, Validators.min(0), Validators.max(1)]],
  });

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.api.submit(this.form.getRawValue()).subscribe({
      next: (r) => {
        this.snack.open(`Submitted ${r.jobId}`, 'OK', { duration: 3000 });
        this.form.reset({ name: '', durationMs: 10000, failureRate: 0 });
      },
      error: (e) => this.snack.open(e?.error?.detail ?? 'Submit failed', 'Dismiss', { duration: 5000 }),
    });
  }
}
