# Distributed Job Monitor — Design

**Date:** 2026-06-10
**Status:** Approved (backend phase)
**Phase:** 1 of 2 — backend only. The Angular 22 frontend is a follow-up with its own spec.

## Purpose

A learning playground demonstrating how live progress of long-running jobs travels
through a distributed system:

- **Apache Pulsar** as a durable work queue (job submission → exactly one worker),
- **Valkey Pub/Sub** as an ephemeral fan-out broadcast (progress events → every app instance),
- **Valkey hashes** as the queryable job state store (because pub/sub alone loses events),
- **STOMP over WebSocket** to push live updates to browsers (each instance forwards only
  to its own connected sessions).

### Learning goals baked into the design

1. **Queue vs fan-out semantics.** Pulsar `Shared` subscription delivers each job to exactly
   one consumer with acks and redelivery. Valkey Pub/Sub delivers each event to every
   subscriber, fire-and-forget. The demo uses both side by side for the jobs they fit.
2. **Why pub/sub alone is not enough.** A client that connects (or refreshes) mid-job missed
   all earlier events. Job state therefore also lives in a Valkey hash, recoverable via REST.
3. **Cross-instance WebSocket routing.** The instance that processes a job is usually not the
   one holding the user's WebSocket. Valkey Pub/Sub bridges that gap; the STOMP simple broker
   on each instance only reaches its local sessions.
4. **Job lifecycle and failure propagation.** `QUEUED → RUNNING → COMPLETED | FAILED`,
   with simulated failures flowing through the same pipeline as progress.
5. **Zero-config local infra.** `spring-boot-docker-compose` service connections start and
   wire Valkey and Pulsar with no connection properties in `application.yaml`.

## Architecture

One Gradle module, one Spring Boot application. **Every instance plays all three roles**
(REST API, Pulsar worker, WebSocket host); distribution comes from running N replicas.

```
Browser ── HTTP POST /jobs ──► instance X
                                 │ 1. write job hash to Valkey (status=QUEUED)
                                 │ 2. send JobCommand to Pulsar topic "jobs.submitted"
                                 ▼
                          Pulsar (Shared subscription "job-workers")
                                 │ exactly one instance consumes
                                 ▼
                              instance Y (worker)
                                 │ simulates work; per step:
                                 │   - update Valkey hash (status, progress)
                                 │   - PUBLISH JobProgressEvent (JSON) on channel "jobs.progress"
                                 ▼
                          Valkey Pub/Sub  ── broadcast ──► ALL instances
                                 │ each instance's Redis message listener
                                 │ calls SimpMessagingTemplate.convertAndSend(...)
                                 │ (simple broker → local STOMP sessions only)
                                 ▼
Browser ◄── STOMP /topic/jobs/{id} ── whichever instance holds the socket
```

Rejected alternatives:
- **Split modules (api / worker / ws-gateway):** more Gradle and config surface, identical
  Pulsar/Valkey/WebSocket semantics — nothing extra learned.
- **No queue (in-process coroutine jobs):** loses the work-distribution half of the demo.

## Components

All under `io.github.rbleuse.playground`, packaged by feature:

| Package | Contents | Responsibility |
|---|---|---|
| `job` | `JobController`, `JobService`, `JobStore`, `Job`, `JobStatus`, DTOs | REST API, job creation, state reads/writes against Valkey hashes |
| `worker` | `JobWorker` (`@PulsarListener`), `JobSimulator` | Consume `JobCommand`s, simulate stepped work, emit progress |
| `progress` | `ProgressPublisher`, `ProgressSubscriber`, `JobProgressEvent` | Valkey channel publish; subscribe → forward to STOMP broker |
| `config` | `WebSocketConfig`, `RedisListenerConfig` | STOMP endpoint/broker setup, `RedisMessageListenerContainer` |

- Dependencies flow inward to `job` (the domain); `worker` and `progress` depend on `job`
  models, never the reverse.
- All DTOs/events are Kotlin data classes, serialized as JSON with Jackson (Kotlin module).
- Constructor injection with `private val` throughout; logger in companion objects.

## REST API

| Endpoint | Request | Response | Notes |
|---|---|---|---|
| `POST /jobs` | `{ "name": str (required, 1–100 chars), "durationMs": long? (default 10000, 1000–120000), "failureRate": double? (default 0.0, 0.0–1.0) }` | `202 Accepted`, `{ "jobId": uuid }`, `Location: /jobs/{id}` | Validated with Bean Validation on a data-class DTO |
| `GET /jobs/{id}` | — | `200` job snapshot, `404` if unknown/expired | Reads the Valkey hash |
| `GET /jobs` | — | `200` list of job snapshots | Backs the future job-list UI |

Job snapshot shape: `{ jobId, name, status, progress (0–100), submittedAt, updatedAt,
workerId?, error? }`.

Errors: global `@RestControllerAdvice` returning RFC 9457 problem details (Spring Boot
default support), covering validation failures and unknown job ids.

## Data model in Valkey

- **Hash** `job:{id}` — fields mirroring the job snapshot. Written by the API on submit
  (QUEUED) and by the worker on every progress step.
- **TTL:** 1 hour, set when the job reaches a terminal state (COMPLETED/FAILED). Running
  jobs do not expire.
- **Index:** set `jobs:index` holding known job ids for `GET /jobs` (members removed lazily
  when their hash has expired).
- Access via `StringRedisTemplate`; values JSON-encoded where structured.

## Messaging contracts

### Pulsar — work queue
- Topic `jobs.submitted`, JSON schema, message value `JobCommand(jobId, name, durationMs, failureRate)`.
- Consumer: `@PulsarListener(subscriptionName = "job-workers", subscriptionType = SHARED)` —
  shared subscription = competing consumers = each job processed exactly once across replicas.
- Failure handling: a job whose simulated failure triggers is **not** nacked — failure is a
  valid business outcome (FAILED state), the message is acked. Nack/redelivery and a
  dead-letter topic are documented in the README as a follow-up exercise, not built now.

### Valkey Pub/Sub — progress fan-out
- Single channel `jobs.progress` (not per-job channels): every instance holds exactly one
  subscription; routing to per-job STOMP topics happens in application code.
- Event: `JobProgressEvent(jobId, name, status, progress, workerId, error?, timestamp)` as JSON.
- Published on every transition: RUNNING (each progress step) and terminal states.

## WebSocket (STOMP)

- Endpoint `/ws` (plain WebSocket handshake, **no SockJS** — avoids sticky-session
  requirements behind a load balancer; documented as a teaching point).
- Simple in-memory broker with destinations:
  - `/topic/jobs/{jobId}` — events for one job,
  - `/topic/jobs` — firehose of all events (job-list view).
- Server-push only; clients never SEND. `ProgressSubscriber` receives a Valkey message,
  deserializes, and forwards to both destinations via `SimpMessagingTemplate`.
- CORS/allowed origins: `http://localhost:4200` (future Angular dev server) and
  `http://localhost:8080`.

## Job simulation

- Worker advances progress in ~10 steps spread over `durationMs`, sleeping between steps
  (simple blocking sleeps on Pulsar listener threads are acceptable for a demo; noted in
  README as the place where real work would go).
- Each step rolls against `failureRate`; on trigger the job transitions to FAILED with an
  error message and stops. `failureRate=1.0` guarantees failure on the first step
  (deterministic for tests); `0.0` (default) never fails.
- `workerId` = instance id (see below) so logs and state show *who* processed the job.

## Instance identity

Each instance derives an id (short random suffix, e.g. `app-3f9c`, exposed as a bean and
logged at startup). It appears in worker logs and in `JobProgressEvent.workerId` for
observability of the distribution. (UI-level display of forwarding-instance identity was
considered and dropped — out of scope.)

## Configuration & local run

- All config in `application.yaml`. **No Valkey/Pulsar connection properties** — supplied by
  docker-compose service connections at dev time and `@ServiceConnection` in tests.
- `compose.yaml` (project root, picked up automatically by `spring-boot-docker-compose`):
  - `valkey/valkey:9.1.0` with label `org.springframework.boot.service-connection: redis`
    (deterministic regardless of image auto-detection support),
  - `apachepulsar/pulsar:4.2.2` running `bin/pulsar standalone` (auto-detected for the
    Pulsar service connection).
- Dev flow: `./gradlew bootRun` → Boot runs `docker compose up`, wires connections, app on
  `:8080`.

### Multi-instance cluster demo

Separate `compose-cluster.yaml` (run manually with `docker compose -f`, *not* via the
dev-time integration):
- app image built with `./gradlew bootBuildImage` (Buildpacks),
- 2 app replicas + Valkey + Pulsar + **nginx** on `:8080` round-robining HTTP and proxying
  WebSocket upgrades (no affinity needed — STOMP rides one persistent connection),
- README walkthrough: submit a job through nginx, observe instance A's logs processing while
  instance B forwards events over your socket.

## Testing

- **Stack:** JUnit 5 + Spring Boot test, **Kotest assertions only** (`kotest-assertions-core`
  6.1.11 — no Kotest runner), Awaitility for async conditions, Testcontainers via Spring
  Boot's BOM with `@ServiceConnection` (`PulsarContainer`, Redis-compatible container running
  `valkey/valkey:9.1.0` with `@ServiceConnection(name = "redis")`).
- **Integration tests** (full `@SpringBootTest(webEnvironment = RANDOM_PORT)`, real Pulsar +
  Valkey, real STOMP client over `WebSocketStompClient`):
  1. **Happy path:** POST a short job → STOMP subscriber on `/topic/jobs/{id}` receives an
     ordered progress stream ending in COMPLETED → `GET /jobs/{id}` shows COMPLETED/100.
  2. **Failure path:** `failureRate=1.0` → FAILED event with error → state FAILED.
  3. **State recovery:** POST a job, *don't* subscribe; after completion `GET /jobs/{id}`
     returns the final state (the "pub/sub loses events, the hash doesn't" lesson).
  4. **Firehose:** `/topic/jobs` receives events for two concurrently submitted jobs.
- **Unit tests** where logic warrants it (e.g. `JobSimulator` step/failure math) — plain
  JUnit + Kotest assertions, no Spring context.

## Versions

| Thing | Version |
|---|---|
| Spring Boot | 4.1.0-RC1 (as scaffolded) |
| Kotlin / JVM | 2.3.20 / Java 25 |
| Valkey | 9.1.0 (`valkey/valkey:9.1.0`) |
| Apache Pulsar | 4.2.2 (`apachepulsar/pulsar:4.2.2`) |
| Kotest assertions | 6.1.11 |
| Testcontainers, Spring Pulsar, Spring Data Redis | managed by Spring Boot BOM |
| Angular | 22.0.0 (phase 2, not in this spec) |

New dependencies to add (starters first, per Boot 4 naming — exact artifact ids confirmed
against the Boot 4.1 dependency BOM during implementation): web MVC, websocket,
data-redis, pulsar, and validation starters, `jackson-module-kotlin`,
`spring-boot-docker-compose` (developmentOnly), and test-scope `spring-boot-testcontainers`,
`org.testcontainers:pulsar`, `org.testcontainers:junit-jupiter`,
`io.kotest:kotest-assertions-core:6.1.11`, `org.awaitility:awaitility`.

## Out of scope (phase 1)

- Angular UI (phase 2 follow-up).
- Authentication/authorization on REST or WebSocket.
- Pulsar nack/retry/dead-letter handling (README exercise only).
- Persistence beyond Valkey; job history beyond the 1-hour TTL.
- Horizontal scaling of the STOMP broker via an external relay (RabbitMQ etc.) — the
  Valkey-bridge pattern *is* the lesson here.
