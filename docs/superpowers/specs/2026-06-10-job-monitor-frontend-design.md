# Distributed Job Monitor — Angular Frontend Design

**Date:** 2026-06-10
**Status:** Approved
**Phase:** 2 of 2 — frontend. Backend spec: [2026-06-10-distributed-job-monitor-design.md](2026-06-10-distributed-job-monitor-design.md).

## Purpose

A single-page Angular dashboard that makes the backend's distributed-progress lesson
visible in a browser: submit long-running jobs, watch every job's live progress as events
fan out from whichever instance processed them, and recover state on refresh from the
Valkey-backed REST snapshot. The frontend consumes the existing contract unchanged — no
backend modifications.

### Learning goals surfaced in the UI

1. **Pub/sub loses events, the hash doesn't.** On load and on opening a job, the UI seeds
   from REST (`GET /jobs`, `GET /jobs/{id}`) before subscribing to live events — a refresh
   never loses already-completed jobs.
2. **Cross-instance routing is invisible to the user.** The socket delivers events for jobs
   processed on other instances; `workerId` in each event shows *who* ran it.
3. **Socket independent of REST.** A connection-status badge reflects the STOMP link
   separately from HTTP calls.
4. **TTL expiry.** Opening a job whose hash has expired returns 404, surfaced as an
   "expired (TTL)" message rather than an error.

## Stack

| Thing | Choice | Why |
|---|---|---|
| Framework | Angular 22, standalone components, signals | Modern default; signals fit a live-updating store |
| Change detection | Zoneless (`provideZonelessChangeDetection`) | No Zone.js; signal-driven updates |
| UI library | Angular Material + CDK | Table, progress bar, dialog, form fields, toolbar, snackbar out of the box |
| WebSocket | `@stomp/stompjs` | STOMP over plain WebSocket `/ws` (no SockJS — matches backend) |
| HTTP | Angular `HttpClient` (`provideHttpClient`) | REST calls |
| Location | `frontend/` subfolder in this repo | Monorepo; nginx serves built output in the cluster demo |
| Dev server | `ng serve` on `:4200` | Already in backend CORS allowlist |

Exact npm package versions confirmed against the Angular 22 release line during
implementation.

## Structure

Feature-grouped, mirroring the backend's package-by-feature layout. Dependencies flow
inward to `core`.

```
frontend/src/app/
  core/
    job.models.ts         # Job, JobStatus, JobProgressEvent, SubmitJobRequest/Response — mirror backend DTOs
    job-api.service.ts    # HttpClient: POST /jobs, GET /jobs, GET /jobs/{id}
    job-stream.service.ts # STOMP client: connect /ws, subscribe topics, expose signals
    job-store.ts          # signal state: Map<jobId, Job>; merges REST seed + live events
  dashboard/
    dashboard.ts          # page: toolbar (status badge) + submit form + jobs table
    submit-form.ts        # Material reactive form; validators mirror Bean Validation
    jobs-table.ts         # MatTable of all jobs, live; row click opens detail dialog
    job-detail-dialog.ts  # MatDialog: live progress bar + event log for one job
  app.config.ts           # providers: HttpClient, zoneless CD, Material, animations
  app.ts                  # root shell hosting <dashboard>
  environments/
    environment.ts        # apiBaseUrl, wsUrl (dev: http://localhost:8080, ws://localhost:8080/ws)
    environment.prod.ts   # same-origin (served behind nginx)
```

Components read store signals only; only the two services touch HTTP/STOMP. The store is the
single source of truth.

## Data model (TypeScript, mirrors backend)

```ts
type JobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';

interface Job {
  jobId: string;
  name: string;
  status: JobStatus;
  progress: number;        // 0–100
  submittedAt: string;     // ISO instant
  updatedAt: string;       // ISO instant
  workerId?: string;
  error?: string;
}

// STOMP payload on /topic/jobs and /topic/jobs/{id}
interface JobProgressEvent {
  jobId: string;
  name: string;
  status: JobStatus;
  progress: number;
  workerId?: string;
  error?: string;
  timestamp: string;       // ISO instant
}

interface SubmitJobRequest {
  name: string;            // required, 1–100 chars
  durationMs?: number;     // default 10000, 1000–120000
  failureRate?: number;    // default 0.0, 0.0–1.0
}

interface SubmitJobResponse { jobId: string; }
```

A `JobProgressEvent` is merged into a `Job` by `jobId` (event fields are a superset minus
`submittedAt`; the store preserves `submittedAt` from the REST seed or first-seen event).

## Data flow

**On load**
1. `job-store` calls `GET /jobs` → seeds the table (proves "the hash survives a refresh").
2. `job-stream` connects `/ws`, subscribes `/topic/jobs` (firehose). Each event is merged
   into the store by `jobId`; the table live-updates. A QUEUED event for an unseen job adds
   a row.

**Submit**
- Form → `POST /jobs` → `202 { jobId }`. No optimistic row is inserted; the firehose QUEUED
  event creates it, making the Pulsar/Valkey round-trip visible. The form clears and a
  snackbar confirms submission.

**Detail dialog**
- Row click opens a `MatDialog` for that `jobId`:
  1. `GET /jobs/{id}` snapshot (recovery lesson again; 404 → "expired (TTL)" message),
  2. subscribe `/topic/jobs/{id}` for that job's live stream,
  3. render a progress bar + a scrolling event log (one line per event: status, progress,
     workerId, timestamp).
- Unsubscribe from `/topic/jobs/{id}` on dialog close. The firehose subscription stays.

## Submit-form validation

Mirror backend Bean Validation so errors are caught client-side before the request:

| Field | Rule | Default |
|---|---|---|
| `name` | required, length 1–100 | — |
| `durationMs` | 1000–120000 | 10000 |
| `failureRate` | 0.0–1.0 | 0.0 |

Server-side rejections (RFC 9457 `application/problem+json`) are still surfaced via snackbar,
parsing the `detail` field — the client validators are a convenience, not the source of truth.

## Error & connection handling

- **STOMP:** `@stomp/stompjs` auto-reconnect (`reconnectDelay`). A connection-status signal
  (`connected | reconnecting | closed`) drives a toolbar badge — teaching point that the
  socket is independent of REST.
- **HTTP errors:** Material snackbar; parse `application/problem+json` `detail` when present.
- **404 on `GET /jobs/{id}`:** dialog shows "job expired (TTL)" — reinforces the 1-hour TTL.

## Dev & build integration

- **Dev:** `ng serve` on `:4200` calls backend `:8080` over CORS (already allowed). STOMP to
  `ws://localhost:8080/ws`. Backend URLs come from `environment.ts`.
- **Prod / cluster demo:** `ng build` produces static assets served by the existing **nginx**
  in `compose-cluster.yaml` — add a `location /` serving the Angular `dist`, keeping the
  existing `/ws`, `/jobs` proxies to the app replicas. Frontend uses same-origin
  (`environment.prod.ts`), so it works through round-robin without affinity (STOMP rides one
  persistent connection, as in the backend design).
- README walkthrough updated: open nginx `:8080`, submit a job, watch the table populate from
  the firehose while one instance's logs show it processing.

## Testing

- **Unit / component:** Angular CLI default test runner with `TestBed`. Cover:
  - `job-store` merge logic (event into existing job; new job from event; terminal-state
    handling),
  - `submit-form` validators (boundary values for each field),
  - `jobs-table` rendering from store signals (services mocked).
- **Service integration:** `job-stream` against a fake STOMP broker + mocked `HttpClient` —
  assert a firehose event updates the store signal and the connection-status signal
  transitions.
- **E2E:** out of scope (follow-up exercise), matching the backend spec's framing.

## Out of scope

- Authentication / authorization.
- Routing beyond the single dashboard page.
- Job history beyond the backend's 1-hour TTL.
- Server-side rendering (SSR).
- Any backend change — the frontend consumes the phase-1 contract as-is.
