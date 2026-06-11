# Distributed Job Monitor (backend)

A learning playground: submit fake long-running jobs, watch live progress stream
from whichever Spring Boot replica processes them to whichever replica holds your
WebSocket — bridged by Valkey Pub/Sub.

## Stack
- Spring Boot 4.1 / Kotlin 2.4 / Java 25
- Apache Pulsar 4.2.2 — durable job queue (Shared subscription = one worker per job)
- Valkey 9.1.0 — job-state hashes + progress Pub/Sub fan-out
- STOMP over WebSocket — live browser push

## Run locally (single instance)
```bash
./gradlew bootRun
```
Spring Boot Docker Compose support starts Valkey + Pulsar automatically.

BootUI developer console (local profile only):
```bash
./gradlew bootRun --args="--spring.profiles.active=local"
```
Open http://localhost:8080/bootui.

Submit a job:
```bash
curl -X POST http://localhost:8080/jobs \
  -H "Content-Type: application/json" \
  -d '{"name":"demo","durationMs":5000,"failureRate":0.0}'
```
Poll its state: `curl http://localhost:8080/jobs/<jobId>`.
Connect a STOMP client to `ws://localhost:8080/ws` and subscribe to
`/topic/jobs/<jobId>` (single job) or `/topic/jobs` (all jobs).

## Run the multi-instance cluster
```bash
./gradlew bootBuildImage --imageName=spring-boot-redis-pub-sub-playground:0.0.1-SNAPSHOT
docker compose -f compose-cluster.yaml up
```
Two app replicas sit behind nginx on `:8080`. Submit a job and watch the logs:
one replica processes it, but events still reach a WebSocket held by the other.

## Frontend

An Angular 22 dashboard lives in `frontend/`: submit jobs and watch a live table
fill from the `/topic/jobs` firehose.

### Dev
```bash
cd frontend && npm start
```
Serves the dashboard on http://localhost:4200 against the backend on `:8080`
(CORS already allows `:4200`). STOMP connects to `ws://localhost:8080/ws`.

### Cluster
```bash
cd frontend && npm run build
```
This produces `frontend/dist/frontend/browser`, which nginx serves as static
files (REST `/jobs` and the `/ws` WebSocket still proxy to the app replicas).
Build the app image first (see above), then bring the stack up:
```bash
./gradlew bootBuildImage --imageName=spring-boot-redis-pub-sub-playground:0.0.1-SNAPSHOT
docker compose -f compose-cluster.yaml up --build
```
Open http://localhost:8080, submit a job, and watch the live table fill from the
`/topic/jobs` firehose while one app instance's logs show it processing the work.

## Architecture
```
POST /jobs ──► instance X ──► Pulsar (jobs.submitted, Shared sub)
                  │ writes QUEUED hash to Valkey        │
                  │                                      ▼
                  │                            instance Y processes
                  │                            updates Valkey hash +
                  │                            PUBLISH jobs.progress
                  ▼                                      │
        GET /jobs/{id} (state recovery)     Valkey Pub/Sub broadcast
                                                         │
                                            every instance forwards to
                                            its local STOMP sessions
```

## Endpoints
| Method | Path | Description |
|---|---|---|
| POST | `/jobs` | Submit a job (`name`, `durationMs`, `failureRate`) → `202` + `jobId` |
| GET | `/jobs/{id}` | Current snapshot (recovers state missed over Pub/Sub) |
| GET | `/jobs` | All known jobs |

WebSocket: `/ws` (STOMP, no SockJS), topics `/topic/jobs/{id}` and `/topic/jobs`.

## Tests
```bash
./gradlew test
```
Testcontainers spins up real Pulsar + Valkey; a real STOMP client asserts the
end-to-end stream. Kotest for assertions.

## Notes & follow-up exercises
- **Jackson 2 vs 3:** Spring Boot 4 defaults to Jackson 3, but Spring Pulsar needs a
  Jackson 2 `ObjectMapper` for its JSON schema — see `config/JacksonConfig.kt`.
- Pulsar nack + dead-letter topic for jobs that should be retried (currently a
  failed job is acked as a valid business outcome).
- External STOMP relay (RabbitMQ) instead of the Valkey bridge — compare the two.
- Phase 2: the Angular 22 frontend.
