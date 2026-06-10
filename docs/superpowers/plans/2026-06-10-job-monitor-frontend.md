# Job Monitor Angular Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a single-page Angular 22 dashboard that submits jobs and shows every job's live progress, consuming the existing backend REST + STOMP contract unchanged.

**Architecture:** Standalone, zoneless, signal-driven Angular app in `frontend/`. A `JobStore` (signals) is the single source of truth; `JobApiService` (HttpClient) seeds it from REST and `JobStreamService` (`@stomp/stompjs`) merges live firehose events. Components are pure signal readers: a submit form, a live MatTable, and a per-job detail dialog.

**Tech Stack:** Angular 22 (standalone components, signals, zoneless change detection), Angular Material + CDK, `@stomp/stompjs`, Angular `HttpClient`, Angular CLI test runner (`TestBed`).

**Spec:** [2026-06-10-job-monitor-frontend-design.md](../specs/2026-06-10-job-monitor-frontend-design.md)

---

## File Structure

```
frontend/
  package.json, angular.json, tsconfig*.json   # CLI-generated
  src/
    main.ts                                     # bootstrapApplication(App, appConfig)
    index.html, styles.scss
    environments/
      environment.ts                            # apiBaseUrl, wsUrl (dev → :8080)
      environment.prod.ts                       # same-origin (behind nginx)
    app/
      app.ts                                    # root shell hosting <app-dashboard>
      app.config.ts                             # providers
      core/
        job.models.ts                           # Job, JobStatus, JobProgressEvent, SubmitJobRequest/Response, mergeEvent()
        job-api.service.ts                      # POST /jobs, GET /jobs, GET /jobs/{id}
        job-api.service.spec.ts
        job-store.ts                            # signal state + seed/apply
        job-store.spec.ts
        job-stream.service.ts                   # STOMP connect/subscribe, status signal
        job-stream.service.spec.ts
      dashboard/
        dashboard.ts / dashboard.html / dashboard.scss
        submit-form.ts / submit-form.html / submit-form.spec.ts
        jobs-table.ts / jobs-table.html / jobs-table.spec.ts
        job-detail-dialog.ts / job-detail-dialog.html
```

**Test-runner note:** All specs use `TestBed` and `describe/it/expect`. If the CLI scaffolds the project with Vitest instead of Karma/Jasmine, enable `globals: true` in the Vitest config (CLI does this by default) so `describe/it/expect` resolve — no test-body changes needed. Run tests non-interactively with `npm test -- --watch=false` (works for both runners).

---

### Task 1: Scaffold the Angular app

**Files:**
- Create: `frontend/` (CLI-generated tree)

- [ ] **Step 1: Generate the app**

Run from repo root:
```bash
npx -p @angular/cli@22 ng new frontend --style=scss --ssr=false --routing=false --skip-git
```
Accept defaults for anything else. `--skip-git` keeps it inside the existing repo.

Expected: `frontend/` created with `package.json`, `angular.json`, `src/app/app.ts`.

- [ ] **Step 2: Verify it builds and the dev server starts**

Run:
```bash
cd frontend && npm run build
```
Expected: build succeeds, `dist/frontend/` produced.

- [ ] **Step 3: Add the WebSocket dependency**

Run from `frontend/`:
```bash
npm install @stomp/stompjs
```
Expected: `@stomp/stompjs` added to `dependencies` in `frontend/package.json`.

- [ ] **Step 4: Commit**

```bash
git add frontend .gitignore
git commit -m "feat(frontend): scaffold Angular 22 app"
```

---

### Task 2: Add Angular Material

**Files:**
- Modify: `frontend/package.json`, `frontend/src/styles.scss`, `frontend/angular.json` (CLI-managed)

- [ ] **Step 1: Install Material**

Run from `frontend/`:
```bash
npx ng add @angular/material --skip-confirmation
```
Choose a prebuilt theme (e.g. Azure/Blue) and accept Angular animations when prompted (non-interactive defaults are fine with `--skip-confirmation`).

Expected: `@angular/material` + `@angular/cdk` in `dependencies`; a theme (`mat.theme()`) imported in `styles.scss`. Note: Angular 22 Material uses the Web Animations API and does **not** install `@angular/animations` or add any animations provider — `app.config.ts` is left untouched by `ng add` (only `provideBrowserGlobalErrorListeners()` is present from scaffolding).

- [ ] **Step 2: Verify build still succeeds**

Run:
```bash
npm run build
```
Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend
git commit -m "feat(frontend): add Angular Material"
```

---

### Task 3: Domain models + event merge

**Files:**
- Create: `frontend/src/app/core/job.models.ts`
- Test: `frontend/src/app/core/job-store.spec.ts` exercises `mergeEvent` indirectly; a dedicated model test is below.
- Create: `frontend/src/app/core/job.models.spec.ts`

- [ ] **Step 1: Write the failing test**

`frontend/src/app/core/job.models.spec.ts`:
```ts
import { mergeEvent, Job, JobProgressEvent } from './job.models';

describe('mergeEvent', () => {
  const base: Job = {
    jobId: 'j1', name: 'resize', status: 'QUEUED', progress: 0,
    submittedAt: '2026-06-10T10:00:00Z', updatedAt: '2026-06-10T10:00:00Z',
  };
  const event: JobProgressEvent = {
    jobId: 'j1', name: 'resize', status: 'RUNNING', progress: 40,
    workerId: 'app-3f9c', timestamp: '2026-06-10T10:00:05Z',
  };

  it('updates an existing job from an event, preserving submittedAt', () => {
    const merged = mergeEvent(base, event);
    expect(merged.status).toBe('RUNNING');
    expect(merged.progress).toBe(40);
    expect(merged.workerId).toBe('app-3f9c');
    expect(merged.updatedAt).toBe('2026-06-10T10:00:05Z');
    expect(merged.submittedAt).toBe('2026-06-10T10:00:00Z');
  });

  it('creates a job from an event when no prior job exists, using timestamp as submittedAt', () => {
    const created = mergeEvent(undefined, event);
    expect(created.jobId).toBe('j1');
    expect(created.submittedAt).toBe('2026-06-10T10:00:05Z');
    expect(created.status).toBe('RUNNING');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --watch=false`
Expected: FAIL — `Cannot find module './job.models'` / `mergeEvent is not a function`.

- [ ] **Step 3: Write the models + merge**

`frontend/src/app/core/job.models.ts`:
```ts
export type JobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface Job {
  jobId: string;
  name: string;
  status: JobStatus;
  progress: number;
  submittedAt: string;
  updatedAt: string;
  workerId?: string;
  error?: string;
}

export interface JobProgressEvent {
  jobId: string;
  name: string;
  status: JobStatus;
  progress: number;
  workerId?: string;
  error?: string;
  timestamp: string;
}

export interface SubmitJobRequest {
  name: string;
  durationMs?: number;
  failureRate?: number;
}

export interface SubmitJobResponse {
  jobId: string;
}

export function isTerminal(status: JobStatus): boolean {
  return status === 'COMPLETED' || status === 'FAILED';
}

/** Merge a live event into a job. When `prior` is undefined, create one (event seen before REST seed). */
export function mergeEvent(prior: Job | undefined, event: JobProgressEvent): Job {
  return {
    jobId: event.jobId,
    name: event.name,
    status: event.status,
    progress: event.progress,
    workerId: event.workerId,
    error: event.error,
    submittedAt: prior?.submittedAt ?? event.timestamp,
    updatedAt: event.timestamp,
  };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --watch=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/job.models.ts frontend/src/app/core/job.models.spec.ts
git commit -m "feat(frontend): job domain models and event merge"
```

---

### Task 4: JobStore (signal state)

**Files:**
- Create: `frontend/src/app/core/job-store.ts`
- Test: `frontend/src/app/core/job-store.spec.ts`

- [ ] **Step 1: Write the failing test**

`frontend/src/app/core/job-store.spec.ts`:
```ts
import { JobStore } from './job-store';
import { Job, JobProgressEvent } from './job.models';

describe('JobStore', () => {
  let store: JobStore;
  beforeEach(() => { store = new JobStore(); });

  const job: Job = {
    jobId: 'j1', name: 'resize', status: 'QUEUED', progress: 0,
    submittedAt: '2026-06-10T10:00:00Z', updatedAt: '2026-06-10T10:00:00Z',
  };

  it('seeds jobs from a REST list, newest-updated first', () => {
    const older: Job = { ...job, jobId: 'j0', updatedAt: '2026-06-10T09:00:00Z' };
    store.seed([job, older]);
    expect(store.jobs().map(j => j.jobId)).toEqual(['j1', 'j0']);
  });

  it('applies an event to an existing job', () => {
    store.seed([job]);
    const event: JobProgressEvent = {
      jobId: 'j1', name: 'resize', status: 'RUNNING', progress: 40,
      workerId: 'app-3f9c', timestamp: '2026-06-10T10:00:05Z',
    };
    store.apply(event);
    expect(store.job('j1')()?.status).toBe('RUNNING');
    expect(store.job('j1')()?.progress).toBe(40);
  });

  it('adds a new job when an event arrives for an unknown id', () => {
    const event: JobProgressEvent = {
      jobId: 'jX', name: 'etl', status: 'QUEUED', progress: 0,
      timestamp: '2026-06-10T10:01:00Z',
    };
    store.apply(event);
    expect(store.jobs().length).toBe(1);
    expect(store.job('jX')()?.name).toBe('etl');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --watch=false`
Expected: FAIL — `Cannot find module './job-store'`.

- [ ] **Step 3: Write the store**

`frontend/src/app/core/job-store.ts`:
```ts
import { Injectable, signal, computed, Signal } from '@angular/core';
import { Job, JobProgressEvent, mergeEvent } from './job.models';

@Injectable({ providedIn: 'root' })
export class JobStore {
  private readonly byId = signal<Map<string, Job>>(new Map());

  /** All jobs, most-recently-updated first. */
  readonly jobs = computed(() =>
    [...this.byId().values()].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)),
  );

  /** Reactive accessor for one job. */
  job(jobId: string): Signal<Job | undefined> {
    return computed(() => this.byId().get(jobId));
  }

  /** Replace state with a REST-loaded list. */
  seed(jobs: Job[]): void {
    this.byId.set(new Map(jobs.map(j => [j.jobId, j])));
  }

  /** Merge a single live event. */
  apply(event: JobProgressEvent): void {
    const next = new Map(this.byId());
    next.set(event.jobId, mergeEvent(next.get(event.jobId), event));
    this.byId.set(next);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --watch=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/job-store.ts frontend/src/app/core/job-store.spec.ts
git commit -m "feat(frontend): signal-based JobStore"
```

---

### Task 5: Environments + JobApiService (REST)

**Files:**
- Create: `frontend/src/environments/environment.ts`, `frontend/src/environments/environment.prod.ts`
- Create: `frontend/src/app/core/job-api.service.ts`
- Test: `frontend/src/app/core/job-api.service.spec.ts`
- Modify: `frontend/src/app/app.config.ts` (add `provideHttpClient`)

- [ ] **Step 1: Create environment files**

`frontend/src/environments/environment.ts`:
```ts
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080',
  wsUrl: 'ws://localhost:8080/ws',
};
```
`frontend/src/environments/environment.prod.ts`:
```ts
export const environment = {
  production: true,
  apiBaseUrl: '',                       // same-origin behind nginx
  wsUrl: `${location.origin.replace(/^http/, 'ws')}/ws`,
};
```

Register the prod replacement in `frontend/angular.json` under the `production` configuration's `fileReplacements`:
```json
{ "replace": "src/environments/environment.ts", "with": "src/environments/environment.prod.ts" }
```

- [ ] **Step 2: Write the failing test**

`frontend/src/app/core/job-api.service.spec.ts`:
```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { JobApiService } from './job-api.service';
import { environment } from '../../environments/environment';

describe('JobApiService', () => {
  let api: JobApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(JobApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('POSTs a submit request and returns the job id', () => {
    let result: string | undefined;
    api.submit({ name: 'resize', durationMs: 5000, failureRate: 0 })
       .subscribe(r => (result = r.jobId));
    const req = http.expectOne(`${environment.apiBaseUrl}/jobs`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.name).toBe('resize');
    req.flush({ jobId: 'j1' });
    expect(result).toBe('j1');
  });

  it('GETs the job list', () => {
    api.list().subscribe();
    const req = http.expectOne(`${environment.apiBaseUrl}/jobs`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('GETs one job by id', () => {
    api.get('j1').subscribe();
    const req = http.expectOne(`${environment.apiBaseUrl}/jobs/j1`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `npm test -- --watch=false`
Expected: FAIL — `Cannot find module './job-api.service'`.

- [ ] **Step 4: Write the service**

`frontend/src/app/core/job-api.service.ts`:
```ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Job, SubmitJobRequest, SubmitJobResponse } from './job.models';

@Injectable({ providedIn: 'root' })
export class JobApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/jobs`;

  submit(req: SubmitJobRequest): Observable<SubmitJobResponse> {
    return this.http.post<SubmitJobResponse>(this.base, req);
  }

  list(): Observable<Job[]> {
    return this.http.get<Job[]>(this.base);
  }

  get(jobId: string): Observable<Job> {
    return this.http.get<Job>(`${this.base}/${jobId}`);
  }
}
```

- [ ] **Step 5: Add `provideHttpClient` to app config**

In `frontend/src/app/app.config.ts`, add `provideHttpClient()` to the `providers` array (import from `@angular/common/http`). Keep the existing `provideBrowserGlobalErrorListeners()`. (Zoneless is added in Task 10; no animations provider exists in Angular 22 Material.)

- [ ] **Step 6: Run test to verify it passes**

Run: `npm test -- --watch=false`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/environments frontend/src/app/core/job-api.service.ts frontend/src/app/core/job-api.service.spec.ts frontend/src/app/app.config.ts frontend/angular.json
git commit -m "feat(frontend): environments and REST JobApiService"
```

---

### Task 6: JobStreamService (STOMP)

**Files:**
- Create: `frontend/src/app/core/job-stream.service.ts`
- Test: `frontend/src/app/core/job-stream.service.spec.ts`

The service wraps a `@stomp/stompjs` `Client`. To keep it testable, the `Client` is created by an overridable protected factory so the spec can inject a fake.

- [ ] **Step 1: Write the failing test**

`frontend/src/app/core/job-stream.service.spec.ts`:
```ts
import { JobStreamService } from './job-stream.service';
import { JobStore } from './job-store';
import { JobProgressEvent } from './job.models';

// Minimal fake matching the bits of @stomp/stompjs Client we use.
class FakeClient {
  onConnect!: () => void;
  onWebSocketClose!: () => void;
  active = false;
  subscriptions: Record<string, (msg: { body: string }) => void> = {};
  activate() { this.active = true; this.onConnect?.(); }
  deactivate() { this.active = false; return Promise.resolve(); }
  subscribe(dest: string, cb: (msg: { body: string }) => void) {
    this.subscriptions[dest] = cb;
    return { unsubscribe: () => delete this.subscriptions[dest] };
  }
  emit(dest: string, event: JobProgressEvent) {
    this.subscriptions[dest]?.({ body: JSON.stringify(event) });
  }
}

class TestableStream extends JobStreamService {
  fake = new FakeClient();
  protected override createClient() { return this.fake as any; }
}

describe('JobStreamService', () => {
  let store: JobStore;
  let stream: TestableStream;

  beforeEach(() => {
    store = new JobStore();
    stream = new TestableStream(store);
  });

  it('sets status to connected on connect and applies firehose events to the store', () => {
    stream.connect();
    expect(stream.status()).toBe('connected');
    const event: JobProgressEvent = {
      jobId: 'j1', name: 'resize', status: 'RUNNING', progress: 50,
      workerId: 'app-1', timestamp: '2026-06-10T10:00:05Z',
    };
    stream.fake.emit('/topic/jobs', event);
    expect(store.job('j1')()?.progress).toBe(50);
  });

  it('reports reconnecting when the socket closes', () => {
    stream.connect();
    stream.fake.onWebSocketClose();
    expect(stream.status()).toBe('reconnecting');
  });

  it('subscribes and unsubscribes a per-job topic', () => {
    stream.connect();
    const seen: JobProgressEvent[] = [];
    const sub = stream.subscribeJob('j1', e => seen.push(e));
    const event: JobProgressEvent = {
      jobId: 'j1', name: 'resize', status: 'COMPLETED', progress: 100,
      timestamp: '2026-06-10T10:00:09Z',
    };
    stream.fake.emit('/topic/jobs/j1', event);
    expect(seen.length).toBe(1);
    sub();
    expect(stream.fake.subscriptions['/topic/jobs/j1']).toBeUndefined();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --watch=false`
Expected: FAIL — `Cannot find module './job-stream.service'`.

- [ ] **Step 3: Write the service**

`frontend/src/app/core/job-stream.service.ts`:
```ts
import { Injectable, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { environment } from '../../environments/environment';
import { JobProgressEvent } from './job.models';
import { JobStore } from './job-store';

export type ConnectionStatus = 'closed' | 'reconnecting' | 'connected';

@Injectable({ providedIn: 'root' })
export class JobStreamService {
  private client?: Client;
  private firehose?: StompSubscription;

  readonly status = signal<ConnectionStatus>('closed');

  // Constructor injection (not field `inject()`): lets the unit test build the service
  // with `new TestableStream(store)` outside an Angular injection context.
  constructor(private readonly store: JobStore) {}

  /** Overridable for tests. */
  protected createClient(): Client {
    return new Client({
      brokerURL: environment.wsUrl,
      reconnectDelay: 5000,
    });
  }

  connect(): void {
    if (this.client) { return; }
    const client = this.createClient();
    this.client = client;
    client.onConnect = () => {
      this.status.set('connected');
      this.firehose = client.subscribe('/topic/jobs', (msg: IMessage) => {
        this.store.apply(JSON.parse(msg.body) as JobProgressEvent);
      });
    };
    client.onWebSocketClose = () => this.status.set('reconnecting');
    this.status.set('reconnecting');
    client.activate();
  }

  /** Subscribe to one job's topic; returns an unsubscribe function. */
  subscribeJob(jobId: string, handler: (event: JobProgressEvent) => void): () => void {
    const sub = this.client!.subscribe(`/topic/jobs/${jobId}`, (msg: IMessage) =>
      handler(JSON.parse(msg.body) as JobProgressEvent),
    );
    return () => sub.unsubscribe();
  }

  disconnect(): void {
    this.firehose?.unsubscribe();
    void this.client?.deactivate();
    this.client = undefined;
    this.status.set('closed');
  }
}
```

Note: the test subclasses the service (`TestableStream extends JobStreamService`) with no explicit constructor, so it inherits `constructor(store)`. `new TestableStream(store)` passes the store directly and `createClient()` is overridden to return the fake — no Angular injection context needed.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --watch=false`
Expected: PASS (3 specs).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/job-stream.service.ts frontend/src/app/core/job-stream.service.spec.ts
git commit -m "feat(frontend): STOMP JobStreamService with connection status"
```

---

### Task 7: Submit form component

**Files:**
- Create: `frontend/src/app/dashboard/submit-form.ts`, `submit-form.html`
- Test: `frontend/src/app/dashboard/submit-form.spec.ts`

- [ ] **Step 1: Write the failing test**

`frontend/src/app/dashboard/submit-form.spec.ts`:
```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { SubmitForm } from './submit-form';

describe('SubmitForm', () => {
  function make() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(SubmitForm);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('is invalid when name is empty', () => {
    const c = make();
    c.form.controls.name.setValue('');
    expect(c.form.controls.name.valid).toBe(false);
  });

  it('rejects durationMs below 1000 and above 120000', () => {
    const c = make();
    c.form.controls.durationMs.setValue(500);
    expect(c.form.controls.durationMs.valid).toBe(false);
    c.form.controls.durationMs.setValue(200000);
    expect(c.form.controls.durationMs.valid).toBe(false);
    c.form.controls.durationMs.setValue(10000);
    expect(c.form.controls.durationMs.valid).toBe(true);
  });

  it('rejects failureRate outside 0..1', () => {
    const c = make();
    c.form.controls.failureRate.setValue(1.5);
    expect(c.form.controls.failureRate.valid).toBe(false);
    c.form.controls.failureRate.setValue(0.5);
    expect(c.form.controls.failureRate.valid).toBe(true);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --watch=false`
Expected: FAIL — `Cannot find module './submit-form'`.

- [ ] **Step 3: Write the component**

`frontend/src/app/dashboard/submit-form.ts`:
```ts
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
    if (this.form.invalid) { return; }
    this.api.submit(this.form.getRawValue()).subscribe({
      next: r => { this.snack.open(`Submitted ${r.jobId}`, 'OK', { duration: 3000 }); this.form.reset({ name: '', durationMs: 10000, failureRate: 0 }); },
      error: e => this.snack.open(e?.error?.detail ?? 'Submit failed', 'Dismiss', { duration: 5000 }),
    });
  }
}
```

`frontend/src/app/dashboard/submit-form.html`:
```html
<mat-card>
  <form [formGroup]="form" (ngSubmit)="submit()" class="submit-row">
    <mat-form-field>
      <mat-label>Name</mat-label>
      <input matInput formControlName="name" maxlength="100" />
    </mat-form-field>
    <mat-form-field>
      <mat-label>Duration (ms)</mat-label>
      <input matInput type="number" formControlName="durationMs" min="1000" max="120000" />
    </mat-form-field>
    <mat-form-field>
      <mat-label>Failure rate</mat-label>
      <input matInput type="number" formControlName="failureRate" min="0" max="1" step="0.1" />
    </mat-form-field>
    <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid">Submit</button>
  </form>
</mat-card>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --watch=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/dashboard/submit-form.ts frontend/src/app/dashboard/submit-form.html frontend/src/app/dashboard/submit-form.spec.ts
git commit -m "feat(frontend): submit form with client-side validation"
```

---

### Task 8: Jobs table component

**Files:**
- Create: `frontend/src/app/dashboard/jobs-table.ts`, `jobs-table.html`
- Test: `frontend/src/app/dashboard/jobs-table.spec.ts`

- [ ] **Step 1: Write the failing test**

`frontend/src/app/dashboard/jobs-table.spec.ts`:
```ts
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --watch=false`
Expected: FAIL — `Cannot find module './jobs-table'`.

- [ ] **Step 3: Write the component**

`frontend/src/app/dashboard/jobs-table.ts`:
```ts
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
```

`frontend/src/app/dashboard/jobs-table.html`:
```html
<table mat-table [dataSource]="jobs()">
  <ng-container matColumnDef="name">
    <th mat-header-cell *matHeaderCellDef>Name</th>
    <td mat-cell *matCellDef="let j">{{ j.name }}</td>
  </ng-container>
  <ng-container matColumnDef="status">
    <th mat-header-cell *matHeaderCellDef>Status</th>
    <td mat-cell *matCellDef="let j">{{ j.status }}</td>
  </ng-container>
  <ng-container matColumnDef="progress">
    <th mat-header-cell *matHeaderCellDef>Progress</th>
    <td mat-cell *matCellDef="let j">
      <mat-progress-bar mode="determinate" [value]="j.progress"></mat-progress-bar>
      <span>{{ j.progress }}%</span>
    </td>
  </ng-container>
  <ng-container matColumnDef="workerId">
    <th mat-header-cell *matHeaderCellDef>Worker</th>
    <td mat-cell *matCellDef="let j">{{ j.workerId ?? '—' }}</td>
  </ng-container>
  <tr mat-header-row *matHeaderRowDef="columns"></tr>
  <tr mat-row *matRowDef="let row; columns: columns" (click)="rowClick.emit(row)" style="cursor:pointer"></tr>
</table>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --watch=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/dashboard/jobs-table.ts frontend/src/app/dashboard/jobs-table.html frontend/src/app/dashboard/jobs-table.spec.ts
git commit -m "feat(frontend): live jobs table"
```

---

### Task 9: Job detail dialog

**Files:**
- Create: `frontend/src/app/dashboard/job-detail-dialog.ts`, `job-detail-dialog.html`

No dedicated spec (thin glue over already-tested store/stream/api); it is exercised via the dashboard. Logic-bearing parts (store merge, stream subscribe) are covered in Tasks 4 and 6.

- [ ] **Step 1: Write the component**

`frontend/src/app/dashboard/job-detail-dialog.ts`:
```ts
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
```

`frontend/src/app/dashboard/job-detail-dialog.html`:
```html
@if (notFound()) {
  <p>Job expired (TTL) — its state is no longer available.</p>
} @else if (job(); as j) {
  <h2 mat-dialog-title>{{ j.name }}</h2>
  <mat-dialog-content>
    <p>Status: {{ j.status }} · Worker: {{ j.workerId ?? '—' }}</p>
    <mat-progress-bar mode="determinate" [value]="j.progress"></mat-progress-bar>
    <span>{{ j.progress }}%</span>
    @if (j.error) { <p class="error">Error: {{ j.error }}</p> }
    <mat-list>
      @for (e of log(); track e.timestamp) {
        <mat-list-item>{{ e.timestamp }} — {{ e.status }} {{ e.progress }}% ({{ e.workerId ?? '—' }})</mat-list-item>
      }
    </mat-list>
  </mat-dialog-content>
} @else {
  <p>Loading…</p>
}
```

- [ ] **Step 2: Verify it compiles**

Run: `npm run build`
Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/dashboard/job-detail-dialog.ts frontend/src/app/dashboard/job-detail-dialog.html
git commit -m "feat(frontend): job detail dialog with live progress + event log"
```

---

### Task 10: Dashboard page + app wiring

**Files:**
- Create: `frontend/src/app/dashboard/dashboard.ts`, `dashboard.html`, `dashboard.scss`
- Modify: `frontend/src/app/app.ts`, `frontend/src/app/app.config.ts`, `frontend/src/app/app.html` (root template)

- [ ] **Step 1: Write the dashboard component**

`frontend/src/app/dashboard/dashboard.ts`:
```ts
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
```

`frontend/src/app/dashboard/dashboard.html`:
```html
<mat-toolbar color="primary">
  <span>Distributed Job Monitor</span>
  <span style="flex:1 1 auto"></span>
  <span>socket: {{ status() }}</span>
</mat-toolbar>

<div class="page">
  <app-submit-form></app-submit-form>
  <app-jobs-table (rowClick)="openJob($event)"></app-jobs-table>
</div>
```

`frontend/src/app/dashboard/dashboard.scss`:
```scss
.page { padding: 16px; display: flex; flex-direction: column; gap: 16px; }
:host ::ng-deep .submit-row { display: flex; gap: 12px; align-items: baseline; flex-wrap: wrap; }
```

- [ ] **Step 2: Mount the dashboard in the root component**

Set `frontend/src/app/app.html` to:
```html
<app-dashboard></app-dashboard>
```
And in `frontend/src/app/app.ts`, add `Dashboard` to the component's `imports`:
```ts
import { Dashboard } from './dashboard/dashboard';
// ...
imports: [Dashboard],
```

- [ ] **Step 3: Confirm providers in app.config.ts**

`frontend/src/app/app.config.ts` `providers` must include (keep the scaffolded `provideBrowserGlobalErrorListeners()`; `provideHttpClient()` was added in Task 5 — do not duplicate):
```ts
provideBrowserGlobalErrorListeners(),
provideZonelessChangeDetection(),
provideHttpClient(),
```
(`provideZonelessChangeDetection` from `@angular/core`, `provideHttpClient` from `@angular/common/http`. Angular 22 Material needs no animations provider.)

- [ ] **Step 4: Run the full test suite and build**

Run:
```bash
npm test -- --watch=false && npm run build
```
Expected: all specs PASS; build succeeds.

- [ ] **Step 5: Manual smoke test against the backend**

In one terminal: `./gradlew bootRun` (repo root). In another: `cd frontend && npm start`. Open `http://localhost:4200`, submit a job, watch a row appear and progress climb to 100%, click the row to see the live event log.
Expected: table populates from the firehose; detail dialog shows progress and worker id.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app
git commit -m "feat(frontend): dashboard page wiring REST seed + live stream"
```

---

### Task 11: nginx static serving + README

**Files:**
- Modify: `nginx/nginx.conf`
- Modify: `compose-cluster.yaml`
- Modify: `README.md`

- [ ] **Step 1: Serve the built Angular app from nginx**

In `nginx/nginx.conf`, inside the existing `server` block, add a root location serving the Angular `dist` with SPA fallback, keeping the existing `/jobs` and `/ws` proxy locations:
```nginx
location / {
    root /usr/share/nginx/html;
    try_files $uri $uri/ /index.html;
}
```
Ensure `/jobs` (REST) and `/ws` (WebSocket upgrade) locations remain and are matched before `/` (nginx longest-prefix match handles this; verify `/ws` keeps `proxy_set_header Upgrade $http_upgrade;` and `Connection "upgrade"`).

- [ ] **Step 2: Mount the build output into the nginx container**

In `compose-cluster.yaml`, add to the nginx service a bind mount (or a build step) exposing the Angular `dist`:
```yaml
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./frontend/dist/frontend/browser:/usr/share/nginx/html:ro
```
Document that `cd frontend && npm run build` must run before `docker compose -f compose-cluster.yaml up`.

- [ ] **Step 3: Update the README walkthrough**

Add a "Frontend" section to `README.md`: dev flow (`npm start` → `:4200` against `bootRun` on `:8080`), and cluster flow (build the frontend, bring up `compose-cluster.yaml`, open nginx `:8080`, submit a job, watch the table fill from the firehose while one instance's logs show it processing the work).

- [ ] **Step 4: Verify the cluster demo end-to-end**

Run:
```bash
cd frontend && npm run build && cd ..
docker compose -f compose-cluster.yaml up --build -d
```
Open `http://localhost:8080`, submit a job, confirm live updates arrive through nginx. Then:
```bash
docker compose -f compose-cluster.yaml down
```
Expected: dashboard served at `:8080`, jobs progress live across replicas.

- [ ] **Step 5: Commit**

```bash
git add nginx/nginx.conf compose-cluster.yaml README.md
git commit -m "feat(frontend): serve Angular build via nginx in cluster demo"
```

---

## Self-Review

**Spec coverage:**
- Stack (Angular 22 / zoneless / Material / @stomp/stompjs / frontend subfolder) → Tasks 1–2, providers in 10. ✓
- Structure (core + dashboard) → Tasks 3–10. ✓
- Data model + mergeEvent → Task 3. ✓
- Data flow: REST seed then firehose → Tasks 4, 5, 10. Submit via POST, row from firehose → Task 7 (no optimistic insert) + 10. Detail dialog GET-then-subscribe → Task 9. ✓
- Submit validation mirrors Bean Validation → Task 7. ✓
- Error/connection: status signal + badge → Tasks 6, 10; snackbar on HTTP error → Task 7; 404 TTL message → Task 9. ✓
- Dev & build integration: environments → Task 5; nginx static serving → Task 11. ✓
- Testing: store/api/stream/form/table specs → Tasks 3–8. ✓
- Out of scope (auth, routing, SSR, backend changes) → respected; no tasks add them. ✓

**Placeholder scan:** No TBD/TODO; every code step has full code. ✓

**Type consistency:** `Job`/`JobProgressEvent`/`SubmitJobRequest` used consistently across tasks; `JobStore.seed/apply/jobs/job`, `JobStreamService.connect/subscribeJob/status/disconnect`, `JobApiService.submit/list/get` names match every call site. ✓
