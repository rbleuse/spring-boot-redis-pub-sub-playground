# Job Monitor — Angular Frontend

Single-page dashboard for the Distributed Job Monitor playground. Submit long-running jobs
and watch every job's live progress as events fan out from whichever backend instance
processed them. Phase 2 of the project — see the repo root [`README.md`](../README.md) and the
specs under [`docs/superpowers/`](../docs/superpowers).

## Stack

- **Angular 22** — standalone components, signals, **zoneless** change detection.
- **Angular Material** + CDK (table, progress bar, dialog, form fields, toolbar, snackbar).
- **`@stomp/stompjs`** — STOMP over a plain WebSocket (`/ws`, no SockJS).
- **Vitest** test runner (CLI 22 default — not Karma/Jasmine).

## Architecture

State flows inward to `core`; components are pure signal readers.

```
src/app/
  core/
    job.models.ts          Job, JobProgressEvent, SubmitJobRequest/Response, mergeEvent()
    job-api.service.ts     HttpClient: POST /jobs, GET /jobs, GET /jobs/{id}
    job-store.ts           signal state (Map<jobId,Job>); single source of truth
    job-stream.service.ts  STOMP client: connect /ws, subscribe topics, connection-status signal
  dashboard/
    dashboard.ts           toolbar (status badge) + submit form + jobs table
    submit-form.ts         reactive form; validators mirror backend Bean Validation
    jobs-table.ts          live MatTable; row click → detail dialog
    job-detail-dialog.ts   GET snapshot then subscribe /topic/jobs/{id}; live progress + event log
  app.config.ts            providers: zoneless CD, HttpClient
  environments/            dev → backend on :8080; prod → same-origin behind nginx
```

### Data flow (the lesson)

1. **On load** — `JobRepository` seeds from `GET /jobs` (the Valkey hash survives a refresh), then
   `JobStreamService` connects `/ws` and subscribes the firehose `/topic/jobs`; every event is
   merged into the store and the table updates live.
2. **Submit** — `POST /jobs` returns `202 {jobId}`. No optimistic row is inserted; the firehose
   `QUEUED` event creates it, making the Pulsar/Valkey round-trip visible.
3. **Detail dialog** — opens with `GET /jobs/{id}` (state recovery; `404` → "expired (TTL)"),
   then subscribes `/topic/jobs/{id}` for that job's live stream. Unsubscribes on close; the
   firehose subscription stays.

## Develop

```bash
npm install
npm start            # ng serve on http://localhost:4200
```

Requires the backend running on `:8080` (`./gradlew bootRun` from the repo root). CORS already
allows `http://localhost:4200`; STOMP connects to `ws://localhost:8080/ws`. Backend URLs live in
`src/environments/environment.ts`.

## Test

```bash
npm test -- --watch=false     # Vitest, single run
```

Specs use `TestBed` with `describe/it/expect`. Coverage: store merge/seed/sort, REST service
URLs (HttpTestingController), STOMP stream against a fake broker, form validators, table render.

## Build

```bash
npm run build        # output: dist/frontend/browser/
```

The production build (`environment.prod.ts`) targets same-origin and is served by **nginx** in
the cluster demo — `compose-cluster.yaml` bind-mounts `dist/frontend/browser/` and proxies
`/jobs` and `/ws` to the app replicas. Build the frontend before bringing the cluster up.

## Backend contract (do not change here)

| | |
|---|---|
| `POST /jobs` | `{ name (1–100), durationMs? (1000–120000, def 10000), failureRate? (0.0–1.0, def 0.0) }` → `202 { jobId }` |
| `GET /jobs` | list of job snapshots |
| `GET /jobs/{id}` | job snapshot, `404` if expired |
| STOMP `/ws` | topics `/topic/jobs` (firehose) and `/topic/jobs/{id}` |

Event/snapshot fields: `jobId, name, status (QUEUED|RUNNING|COMPLETED|FAILED), progress (0–100),
workerId?, error?` (+ `submittedAt`/`updatedAt` on snapshots, `timestamp` on events).

## Out of scope

Auth, routing beyond the single page, job history beyond the backend's 1-hour TTL, SSR, and
end-to-end tests (follow-up exercise).
