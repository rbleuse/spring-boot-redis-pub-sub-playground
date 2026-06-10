# Distributed Job Monitor — Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the backend of a distributed job monitor where any Spring Boot replica can accept a job over REST, exactly one replica processes it (Pulsar shared subscription), and live progress fans out via Valkey Pub/Sub to whichever replica holds the user's STOMP WebSocket.

**Architecture:** One Spring Boot 4.1 / Kotlin app; every replica is REST API + Pulsar worker + WebSocket host. `POST /jobs` writes a `QUEUED` job hash to Valkey and sends a `JobCommand` to Pulsar topic `jobs.submitted`. A `Shared` subscription means one worker consumes it, simulates stepped work, updates the Valkey hash, and `PUBLISH`es `JobProgressEvent` JSON on the Valkey channel `jobs.progress`. Every replica subscribes to that channel and forwards events to its local STOMP simple broker (`/topic/jobs/{id}` and `/topic/jobs`).

**Tech Stack:** Spring Boot 4.1.0-RC1, Kotlin 2.4.0, Java 25, Spring Pulsar, Spring Data Redis, Spring WebSocket (STOMP), Apache Pulsar 4.2.2, Valkey 9.1.0, Testcontainers, Kotest assertions 6.1.11, Awaitility.

**Spec:** `docs/superpowers/specs/2026-06-10-distributed-job-monitor-design.md`

---

## File Structure

All production code under `src/main/kotlin/io/github/rbleuse/playground/`:

| File | Responsibility |
|---|---|
| `Constants.kt` | Topic/channel/destination name constants (single source of truth) |
| `instance/InstanceInfo.kt` | Per-replica identity (`id`), provided as a bean |
| `job/Job.kt` | Domain snapshot + `JobStatus` enum + `toEvent()` mapping |
| `job/JobDtos.kt` | `SubmitJobRequest` (validated), `SubmitJobResponse` |
| `job/JobCommand.kt` | Pulsar message payload (`@PulsarMessage` JSON) |
| `job/JobStore.kt` | Valkey hash read/write + index set + terminal TTL |
| `job/JobService.kt` | Create job: persist QUEUED + send to Pulsar; read snapshots |
| `job/JobController.kt` | REST endpoints `POST /jobs`, `GET /jobs/{id}`, `GET /jobs` |
| `job/JobErrors.kt` | `JobNotFoundException` + `@RestControllerAdvice` handler |
| `progress/JobProgressEvent.kt` | Fan-out event payload (JSON over Valkey) |
| `progress/ProgressPublisher.kt` | `convertAndSend` event JSON to Valkey channel |
| `progress/ProgressSubscriber.kt` | Valkey `MessageListener` → forward to STOMP broker |
| `progress/ProgressReporter.kt` | Atomic "save snapshot + publish event" used by the worker |
| `worker/JobSimulator.kt` | Pure step/progress/failure math (unit-tested) |
| `worker/JobProcessor.kt` | Drive a job through RUNNING→terminal using the simulator |
| `worker/JobWorker.kt` | `@PulsarListener` entry point |
| `config/WebSocketConfig.kt` | `@EnableWebSocketMessageBroker`, `/ws` endpoint, simple broker |
| `config/RedisListenerConfig.kt` | `RedisMessageListenerContainer` wiring the subscriber |
| `config/PulsarConfig.kt` | `SchemaResolverCustomizer` → use Spring's Kotlin ObjectMapper |

Test code under `src/test/kotlin/io/github/rbleuse/playground/`:

| File | Responsibility |
|---|---|
| `worker/JobSimulatorTest.kt` | Pure unit tests, no Spring context |
| `support/IntegrationTest.kt` | Base class: `@SpringBootTest(RANDOM_PORT)` + Testcontainers `@ServiceConnection` for Pulsar + Valkey |
| `job/JobStoreIntegrationTest.kt` | Store round-trip, index, TTL behaviour |
| `JobMonitorIntegrationTest.kt` | End-to-end: happy path, failure, state recovery, firehose |

---

## Task 0: Build configuration & dependencies

**Files:**
- Modify: `build.gradle.kts`
- Create: `compose.yaml`
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: Bump Kotlin and add dependencies**

Replace the `plugins` and `dependencies` blocks in `build.gradle.kts` so the file reads:

```kotlin
plugins {
	kotlin("jvm") version "2.4.0"
	kotlin("plugin.spring") version "2.4.0"
	id("org.springframework.boot") version "4.1.0-RC1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.rbleuse"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-pulsar")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:pulsar")
	testImplementation("io.kotest:kotest-assertions-core:6.1.11")
	testImplementation("org.awaitility:awaitility")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
```

> Note: the Spring milestone repo is required because 4.1.0-RC1 is a release candidate. The Testcontainers Redis module is not needed — we use a `GenericContainer` with `@ServiceConnection(name = "redis")` for Valkey (see Task 9 base class).

- [ ] **Step 2: Create `compose.yaml` at project root**

```yaml
services:
  valkey:
    image: 'valkey/valkey:9.1.0'
    ports:
      - '6379'
    labels:
      org.springframework.boot.service-connection: redis
  pulsar:
    image: 'apachepulsar/pulsar:4.2.2'
    command: [ 'bin/pulsar', 'standalone' ]
    ports:
      - '6650'
      - '8080'
```

> The explicit `service-connection: redis` label makes Boot wire Valkey as a Redis connection regardless of image auto-detection. Pulsar is auto-detected by image name.

- [ ] **Step 3: Set application config**

Replace `src/main/resources/application.yaml` with:

```yaml
spring:
  application:
    name: spring-boot-redis-pub-sub-playground
  pulsar:
    producer:
      topic-name: jobs.submitted
    consumer:
      subscription:
        type: shared

logging:
  level:
    io.github.rbleuse.playground: INFO
```

> No Valkey/Pulsar connection properties — supplied by docker-compose service connections at dev time and `@ServiceConnection` in tests.

- [ ] **Step 4: Verify the build resolves**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL` (dependencies resolve; no compilation yet).

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts compose.yaml src/main/resources/application.yaml
git commit -m "build: add web/websocket/redis/pulsar deps and compose services"
```

---

## Task 1: Constants and instance identity

**Files:**
- Create: `src/main/kotlin/io/github/rbleuse/playground/Constants.kt`
- Create: `src/main/kotlin/io/github/rbleuse/playground/instance/InstanceInfo.kt`
- Modify: `src/main/kotlin/io/github/rbleuse/playground/SpringBootRedisPubSubPlaygroundApplication.kt`

- [ ] **Step 1: Create constants**

```kotlin
package io.github.rbleuse.playground

object Topics {
    const val JOBS_SUBMITTED = "jobs.submitted"
}

object Channels {
    const val JOBS_PROGRESS = "jobs.progress"
}

object Destinations {
    const val JOBS_FIREHOSE = "/topic/jobs"
    fun job(jobId: String) = "/topic/jobs/$jobId"
}

object RedisKeys {
    fun job(jobId: String) = "job:$jobId"
    const val JOB_INDEX = "jobs:index"
}
```

- [ ] **Step 2: Create the instance identity bean**

```kotlin
package io.github.rbleuse.playground.instance

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.random.Random

data class InstanceInfo(val id: String)

@Configuration
class InstanceConfig {
    @Bean
    fun instanceInfo(): InstanceInfo {
        val id = "app-%04x".format(Random.nextInt(0, 0x10000))
        logger.info("This instance id is {}", id)
        return InstanceInfo(id)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InstanceConfig::class.java)
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/rbleuse/playground/Constants.kt src/main/kotlin/io/github/rbleuse/playground/instance/InstanceInfo.kt
git commit -m "feat: add name constants and per-instance identity bean"
```

---

## Task 2: Domain model, DTOs, command and event payloads

**Files:**
- Create: `src/main/kotlin/io/github/rbleuse/playground/job/Job.kt`
- Create: `src/main/kotlin/io/github/rbleuse/playground/job/JobDtos.kt`
- Create: `src/main/kotlin/io/github/rbleuse/playground/job/JobCommand.kt`
- Create: `src/main/kotlin/io/github/rbleuse/playground/progress/JobProgressEvent.kt`

- [ ] **Step 1: Create the domain snapshot and status**

```kotlin
package io.github.rbleuse.playground.job

import io.github.rbleuse.playground.progress.JobProgressEvent
import java.time.Instant

enum class JobStatus {
    QUEUED, RUNNING, COMPLETED, FAILED;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED
}

data class Job(
    val jobId: String,
    val name: String,
    val status: JobStatus,
    val progress: Int,
    val submittedAt: Instant,
    val updatedAt: Instant,
    val workerId: String? = null,
    val error: String? = null,
) {
    fun toEvent(): JobProgressEvent = JobProgressEvent(
        jobId = jobId,
        name = name,
        status = status,
        progress = progress,
        workerId = workerId,
        error = error,
        timestamp = updatedAt,
    )
}
```

- [ ] **Step 2: Create the REST DTOs with validation**

```kotlin
package io.github.rbleuse.playground.job

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SubmitJobRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 100)
    val name: String,

    @field:Min(1000)
    @field:Max(120_000)
    val durationMs: Long = 10_000,

    @field:DecimalMin("0.0")
    @field:DecimalMax("1.0")
    val failureRate: Double = 0.0,
)

data class SubmitJobResponse(val jobId: String)
```

- [ ] **Step 3: Create the Pulsar command payload**

```kotlin
package io.github.rbleuse.playground.job

import org.apache.pulsar.common.schema.SchemaType
import org.springframework.pulsar.annotation.PulsarMessage

@PulsarMessage(schemaType = SchemaType.JSON)
data class JobCommand(
    val jobId: String,
    val name: String,
    val durationMs: Long,
    val failureRate: Double,
)
```

- [ ] **Step 4: Create the fan-out event payload**

```kotlin
package io.github.rbleuse.playground.progress

import io.github.rbleuse.playground.job.JobStatus
import java.time.Instant

data class JobProgressEvent(
    val jobId: String,
    val name: String,
    val status: JobStatus,
    val progress: Int,
    val workerId: String?,
    val error: String?,
    val timestamp: Instant,
)
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/rbleuse/playground/job/Job.kt src/main/kotlin/io/github/rbleuse/playground/job/JobDtos.kt src/main/kotlin/io/github/rbleuse/playground/job/JobCommand.kt src/main/kotlin/io/github/rbleuse/playground/progress/JobProgressEvent.kt
git commit -m "feat: add job domain model, DTOs, command and progress event"
```

---

## Task 3: Integration test harness (Testcontainers base class)

**Files:**
- Create: `src/test/kotlin/io/github/rbleuse/playground/support/IntegrationTest.kt`

This base class boots the full app with real Valkey + Pulsar containers wired through `@ServiceConnection`. Later integration tests extend it.

- [ ] **Step 1: Write the base class**

```kotlin
package io.github.rbleuse.playground.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PulsarContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
abstract class IntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val pulsar = PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:4.2.2"))

        @Container
        @ServiceConnection(name = "redis")
        @JvmStatic
        val valkey: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("valkey/valkey:9.1.0")).withExposedPorts(6379)
    }
}
```

> `@ServiceConnection(name = "redis")` tells Boot to treat the generic Valkey container as a Redis service connection (the image alone is not auto-recognised). `PulsarContainer` is auto-recognised.

- [ ] **Step 2: Verify it compiles (no test yet to run)**

Run: `./gradlew compileTestKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/io/github/rbleuse/playground/support/IntegrationTest.kt
git commit -m "test: add Testcontainers base class for Pulsar + Valkey"
```

---

## Task 4: JobStore — Valkey hash persistence (TDD)

**Files:**
- Create: `src/main/kotlin/io/github/rbleuse/playground/job/JobStore.kt`
- Test: `src/test/kotlin/io/github/rbleuse/playground/job/JobStoreIntegrationTest.kt`

- [ ] **Step 1: Write the failing integration test**

```kotlin
package io.github.rbleuse.playground.job

import io.github.rbleuse.playground.support.IntegrationTest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

class JobStoreIntegrationTest @Autowired constructor(
    private val store: JobStore,
) : IntegrationTest() {

    private fun newJob(status: JobStatus = JobStatus.QUEUED) = Job(
        jobId = UUID.randomUUID().toString(),
        name = "test-job",
        status = status,
        progress = if (status == JobStatus.COMPLETED) 100 else 0,
        submittedAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun `saves and reads back a job`() {
        val job = newJob()
        store.save(job)

        val found = store.find(job.jobId)

        found.shouldNotBeNull()
        found.jobId shouldBe job.jobId
        found.name shouldBe "test-job"
        found.status shouldBe JobStatus.QUEUED
    }

    @Test
    fun `find returns null for unknown job`() {
        store.find("does-not-exist").shouldBeNull()
    }

    @Test
    fun `findAll lists saved jobs`() {
        val job = newJob()
        store.save(job)

        store.findAll().map { it.jobId } shouldContain job.jobId
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew test --tests "io.github.rbleuse.playground.job.JobStoreIntegrationTest"`
Expected: FAIL — `JobStore` does not exist (compilation error).

- [ ] **Step 3: Implement `JobStore`**

```kotlin
package io.github.rbleuse.playground.job

import io.github.rbleuse.playground.RedisKeys
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant

@Repository
class JobStore(private val redis: StringRedisTemplate) {

    fun save(job: Job) {
        val key = RedisKeys.job(job.jobId)
        redis.opsForHash<String, String>().putAll(key, job.toHash())
        redis.opsForSet().add(RedisKeys.JOB_INDEX, job.jobId)
        if (job.status.isTerminal) {
            redis.expire(key, TERMINAL_TTL)
        }
    }

    fun find(jobId: String): Job? {
        val map = redis.opsForHash<String, String>().entries(RedisKeys.job(jobId))
        return if (map.isEmpty()) null else map.toJob()
    }

    fun findAll(): List<Job> =
        redis.opsForSet().members(RedisKeys.JOB_INDEX).orEmpty()
            .mapNotNull { find(it) }

    private fun Job.toHash(): Map<String, String> = buildMap {
        put("jobId", jobId)
        put("name", name)
        put("status", status.name)
        put("progress", progress.toString())
        put("submittedAt", submittedAt.toString())
        put("updatedAt", updatedAt.toString())
        workerId?.let { put("workerId", it) }
        error?.let { put("error", it) }
    }

    private fun Map<String, String>.toJob(): Job = Job(
        jobId = getValue("jobId"),
        name = getValue("name"),
        status = JobStatus.valueOf(getValue("status")),
        progress = getValue("progress").toInt(),
        submittedAt = Instant.parse(getValue("submittedAt")),
        updatedAt = Instant.parse(getValue("updatedAt")),
        workerId = this["workerId"],
        error = this["error"],
    )

    companion object {
        private val TERMINAL_TTL: Duration = Duration.ofHours(1)
    }
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `./gradlew test --tests "io.github.rbleuse.playground.job.JobStoreIntegrationTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/rbleuse/playground/job/JobStore.kt src/test/kotlin/io/github/rbleuse/playground/job/JobStoreIntegrationTest.kt
git commit -m "feat: add Valkey-backed JobStore with index and terminal TTL"
```

---

## Task 5: JobSimulator — pure step/failure math (TDD, unit)

**Files:**
- Create: `src/main/kotlin/io/github/rbleuse/playground/worker/JobSimulator.kt`
- Test: `src/test/kotlin/io/github/rbleuse/playground/worker/JobSimulatorTest.kt`

- [ ] **Step 1: Write the failing unit test**

```kotlin
package io.github.rbleuse.playground.worker

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class JobSimulatorTest {

    private val simulator = JobSimulator(steps = 10)

    @Test
    fun `progress is evenly distributed across steps`() {
        simulator.progressAt(0) shouldBe 0
        simulator.progressAt(5) shouldBe 50
        simulator.progressAt(10) shouldBe 100
    }

    @Test
    fun `step delay divides total duration by steps`() {
        simulator.stepDelayMs(10_000) shouldBe 1_000
    }

    @Test
    fun `shouldFail is true when roll is below failure rate`() {
        simulator.shouldFail(failureRate = 1.0, roll = 0.0) shouldBe true
        simulator.shouldFail(failureRate = 0.0, roll = 0.0) shouldBe false
        simulator.shouldFail(failureRate = 0.5, roll = 0.4) shouldBe true
        simulator.shouldFail(failureRate = 0.5, roll = 0.6) shouldBe false
    }

    @Test
    fun `totalSteps is exposed`() {
        simulator.totalSteps shouldBe 10
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew test --tests "io.github.rbleuse.playground.worker.JobSimulatorTest"`
Expected: FAIL — `JobSimulator` does not exist.

- [ ] **Step 3: Implement `JobSimulator`**

```kotlin
package io.github.rbleuse.playground.worker

import org.springframework.stereotype.Component

@Component
class JobSimulator(private val steps: Int = 10) {

    val totalSteps: Int get() = steps

    fun progressAt(step: Int): Int = (step * 100) / steps

    fun stepDelayMs(durationMs: Long): Long = durationMs / steps

    fun shouldFail(failureRate: Double, roll: Double): Boolean = roll < failureRate
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `./gradlew test --tests "io.github.rbleuse.playground.worker.JobSimulatorTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/rbleuse/playground/worker/JobSimulator.kt src/test/kotlin/io/github/rbleuse/playground/worker/JobSimulatorTest.kt
git commit -m "feat: add pure JobSimulator step/progress/failure math"
```

---

## Task 6: Progress publish + subscribe wiring

**Files:**
- Create: `src/main/kotlin/io/github/rbleuse/playground/progress/ProgressPublisher.kt`
- Create: `src/main/kotlin/io/github/rbleuse/playground/progress/ProgressSubscriber.kt`
- Create: `src/main/kotlin/io/github/rbleuse/playground/progress/ProgressReporter.kt`
- Create: `src/main/kotlin/io/github/rbleuse/playground/config/RedisListenerConfig.kt`

These are wired/verified end-to-end in Task 10; here we just build the units so they compile and are bean-wired.

- [ ] **Step 1: Create `ProgressPublisher`**

```kotlin
package io.github.rbleuse.playground.progress

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.rbleuse.playground.Channels
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class ProgressPublisher(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun publish(event: JobProgressEvent) {
        redis.convertAndSend(Channels.JOBS_PROGRESS, objectMapper.writeValueAsString(event))
    }
}
```

- [ ] **Step 2: Create `ProgressSubscriber`**

```kotlin
package io.github.rbleuse.playground.progress

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.rbleuse.playground.Destinations
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class ProgressSubscriber(
    private val messaging: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper,
) : MessageListener {

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val event = objectMapper.readValue(message.body, JobProgressEvent::class.java)
        logger.debug("Forwarding progress for job {} ({})", event.jobId, event.status)
        messaging.convertAndSend(Destinations.job(event.jobId), event)
        messaging.convertAndSend(Destinations.JOBS_FIREHOSE, event)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProgressSubscriber::class.java)
    }
}
```

- [ ] **Step 3: Create `ProgressReporter`** (used by the worker to persist + broadcast together)

```kotlin
package io.github.rbleuse.playground.progress

import io.github.rbleuse.playground.job.Job
import io.github.rbleuse.playground.job.JobStore
import org.springframework.stereotype.Service

@Service
class ProgressReporter(
    private val store: JobStore,
    private val publisher: ProgressPublisher,
) {
    /** Persist the new snapshot then broadcast it. */
    fun report(job: Job) {
        store.save(job)
        publisher.publish(job.toEvent())
    }
}
```

- [ ] **Step 4: Create `RedisListenerConfig`** wiring the subscriber to the channel

```kotlin
package io.github.rbleuse.playground.config

import io.github.rbleuse.playground.Channels
import io.github.rbleuse.playground.progress.ProgressSubscriber
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.ChannelTopic

@Configuration
class RedisListenerConfig {

    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
        subscriber: ProgressSubscriber,
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        container.addMessageListener(subscriber, ChannelTopic(Channels.JOBS_PROGRESS))
        return container
    }
}
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/rbleuse/playground/progress src/main/kotlin/io/github/rbleuse/playground/config/RedisListenerConfig.kt
git commit -m "feat: add Valkey progress publisher, subscriber, reporter and listener container"
```

---

## Task 7: WebSocket (STOMP) and Pulsar object-mapper config

**Files:**
- Create: `src/main/kotlin/io/github/rbleuse/playground/config/WebSocketConfig.kt`
- Create: `src/main/kotlin/io/github/rbleuse/playground/config/PulsarConfig.kt`

- [ ] **Step 1: Create `WebSocketConfig`**

```kotlin
package io.github.rbleuse.playground.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // No SockJS: a single persistent connection avoids sticky-session needs behind nginx.
        registry.addEndpoint("/ws")
            .setAllowedOrigins("http://localhost:4200", "http://localhost:8080")
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
    }
}
```

- [ ] **Step 2: Create `PulsarConfig`** so JSON schema uses Spring's Kotlin-aware ObjectMapper

```kotlin
package io.github.rbleuse.playground.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.pulsar.core.DefaultSchemaResolver
import org.springframework.pulsar.core.SchemaResolverCustomizer

@Configuration
class PulsarConfig {

    /**
     * Pulsar's default JSON schema uses a shaded ObjectMapper without the Kotlin module,
     * which cannot deserialize Kotlin data classes. Point it at Spring's mapper instead.
     */
    @Bean
    fun schemaResolverCustomizer(objectMapper: ObjectMapper): SchemaResolverCustomizer<DefaultSchemaResolver> =
        SchemaResolverCustomizer { resolver -> resolver.objectMapper = objectMapper }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/rbleuse/playground/config/WebSocketConfig.kt src/main/kotlin/io/github/rbleuse/playground/config/PulsarConfig.kt
git commit -m "feat: add STOMP WebSocket config and Pulsar Kotlin ObjectMapper customizer"
```

---

## Task 8: JobProcessor and JobWorker — consume and simulate

**Files:**
- Create: `src/main/kotlin/io/github/rbleuse/playground/worker/JobProcessor.kt`
- Create: `src/main/kotlin/io/github/rbleuse/playground/worker/JobWorker.kt`

- [ ] **Step 1: Create `JobProcessor`** (drives a job RUNNING → terminal)

```kotlin
package io.github.rbleuse.playground.worker

import io.github.rbleuse.playground.instance.InstanceInfo
import io.github.rbleuse.playground.job.Job
import io.github.rbleuse.playground.job.JobCommand
import io.github.rbleuse.playground.job.JobStatus
import io.github.rbleuse.playground.job.JobStore
import io.github.rbleuse.playground.progress.ProgressReporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.random.Random

@Service
class JobProcessor(
    private val store: JobStore,
    private val reporter: ProgressReporter,
    private val simulator: JobSimulator,
    private val instance: InstanceInfo,
    private val random: Random = Random.Default,
) {
    fun process(command: JobCommand) {
        logger.info("Instance {} processing job {} ({})", instance.id, command.jobId, command.name)
        val base = store.find(command.jobId) ?: baseJob(command)

        for (step in 1..simulator.totalSteps) {
            Thread.sleep(simulator.stepDelayMs(command.durationMs))
            if (simulator.shouldFail(command.failureRate, random.nextDouble())) {
                reporter.report(
                    base.copy(
                        status = JobStatus.FAILED,
                        workerId = instance.id,
                        error = "Job failed at step $step of ${simulator.totalSteps}",
                        updatedAt = Instant.now(),
                    )
                )
                logger.info("Job {} FAILED at step {}", command.jobId, step)
                return
            }
            reporter.report(
                base.copy(
                    status = JobStatus.RUNNING,
                    progress = simulator.progressAt(step),
                    workerId = instance.id,
                    updatedAt = Instant.now(),
                )
            )
        }

        reporter.report(
            base.copy(
                status = JobStatus.COMPLETED,
                progress = 100,
                workerId = instance.id,
                updatedAt = Instant.now(),
            )
        )
        logger.info("Job {} COMPLETED", command.jobId)
    }

    private fun baseJob(command: JobCommand) = Job(
        jobId = command.jobId,
        name = command.name,
        status = JobStatus.QUEUED,
        progress = 0,
        submittedAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    companion object {
        private val logger = LoggerFactory.getLogger(JobProcessor::class.java)
    }
}
```

- [ ] **Step 2: Create `JobWorker`** (the Pulsar entry point)

```kotlin
package io.github.rbleuse.playground.worker

import io.github.rbleuse.playground.job.JobCommand
import org.apache.pulsar.client.api.SubscriptionType
import org.apache.pulsar.common.schema.SchemaType
import org.springframework.pulsar.annotation.PulsarListener
import org.springframework.stereotype.Component

@Component
class JobWorker(private val processor: JobProcessor) {

    @PulsarListener(
        subscriptionName = "job-workers",
        topics = ["jobs.submitted"],
        subscriptionType = [SubscriptionType.Shared],
        schemaType = SchemaType.JSON,
    )
    fun onJobSubmitted(command: JobCommand) {
        processor.process(command)
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/rbleuse/playground/worker/JobProcessor.kt src/main/kotlin/io/github/rbleuse/playground/worker/JobWorker.kt
git commit -m "feat: add JobProcessor simulation and Pulsar shared-subscription worker"
```

---

## Task 9: JobService, controller and error handling

**Files:**
- Create: `src/main/kotlin/io/github/rbleuse/playground/job/JobService.kt`
- Create: `src/main/kotlin/io/github/rbleuse/playground/job/JobController.kt`
- Create: `src/main/kotlin/io/github/rbleuse/playground/job/JobErrors.kt`

- [ ] **Step 1: Create `JobService`** (persist QUEUED + send to Pulsar)

```kotlin
package io.github.rbleuse.playground.job

import io.github.rbleuse.playground.Topics
import org.slf4j.LoggerFactory
import org.springframework.pulsar.core.PulsarTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class JobService(
    private val store: JobStore,
    private val pulsarTemplate: PulsarTemplate<JobCommand>,
) {
    fun submit(request: SubmitJobRequest): String {
        val jobId = UUID.randomUUID().toString()
        val now = Instant.now()
        store.save(
            Job(
                jobId = jobId,
                name = request.name,
                status = JobStatus.QUEUED,
                progress = 0,
                submittedAt = now,
                updatedAt = now,
            )
        )
        pulsarTemplate.send(
            Topics.JOBS_SUBMITTED,
            JobCommand(jobId, request.name, request.durationMs, request.failureRate),
        )
        logger.info("Queued job {} ({})", jobId, request.name)
        return jobId
    }

    fun get(jobId: String): Job = store.find(jobId) ?: throw JobNotFoundException(jobId)

    fun list(): List<Job> = store.findAll()

    companion object {
        private val logger = LoggerFactory.getLogger(JobService::class.java)
    }
}
```

- [ ] **Step 2: Create `JobController`**

```kotlin
package io.github.rbleuse.playground.job

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/jobs")
class JobController(private val service: JobService) {

    @PostMapping
    fun submit(@Valid @RequestBody request: SubmitJobRequest): ResponseEntity<SubmitJobResponse> {
        val jobId = service.submit(request)
        return ResponseEntity.accepted()
            .location(URI.create("/jobs/$jobId"))
            .body(SubmitJobResponse(jobId))
    }

    @GetMapping("/{jobId}")
    fun get(@PathVariable jobId: String): Job = service.get(jobId)

    @GetMapping
    fun list(): List<Job> = service.list()
}
```

- [ ] **Step 3: Create `JobErrors`** (exception + advice → RFC 9457 problem detail)

```kotlin
package io.github.rbleuse.playground.job

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class JobNotFoundException(jobId: String) : RuntimeException("Job not found: $jobId")

@RestControllerAdvice
class JobExceptionHandler {

    @ExceptionHandler(JobNotFoundException::class)
    fun handleNotFound(ex: JobNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Job not found")
}
```

> Bean-validation failures on `@Valid` bodies are already turned into 400 problem details by Spring Boot's default `ProblemDetail` support — no extra handler needed.

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/rbleuse/playground/job/JobService.kt src/main/kotlin/io/github/rbleuse/playground/job/JobController.kt src/main/kotlin/io/github/rbleuse/playground/job/JobErrors.kt
git commit -m "feat: add job REST API, service and problem-detail error handling"
```

---

## Task 10: End-to-end integration tests (TDD of the whole pipeline)

**Files:**
- Create: `src/test/kotlin/io/github/rbleuse/playground/JobMonitorIntegrationTest.kt`

This exercises the full path: REST → Pulsar → worker → Valkey hash + Pub/Sub → STOMP client.

- [ ] **Step 1: Write the failing end-to-end test**

```kotlin
package io.github.rbleuse.playground

import io.github.rbleuse.playground.job.SubmitJobResponse
import io.github.rbleuse.playground.progress.JobProgressEvent
import io.github.rbleuse.playground.support.IntegrationTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.client.RestClient
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

class JobMonitorIntegrationTest @Autowired constructor(
    @param:LocalServerPort private val port: Int,
) : IntegrationTest() {

    private val rest = RestClient.create()

    private fun stompSession(): StompSession {
        val client = WebSocketStompClient(StandardWebSocketClient())
        client.messageConverter = MappingJackson2MessageConverter()
        return client.connectAsync(
            "ws://localhost:$port/ws",
            object : StompSessionHandlerAdapter() {},
        ).get(5, TimeUnit.SECONDS)
    }

    private fun StompSession.collect(destination: String): LinkedBlockingDeque<JobProgressEvent> {
        val events = LinkedBlockingDeque<JobProgressEvent>()
        subscribe(destination, object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = JobProgressEvent::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                events.add(payload as JobProgressEvent)
            }
        })
        return events
    }

    private fun submit(name: String, durationMs: Long = 2000, failureRate: Double = 0.0): String {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = mapOf("name" to name, "durationMs" to durationMs, "failureRate" to failureRate)
        return rest.post().uri("http://localhost:$port/jobs")
            .headers { it.addAll(headers) }
            .body(body)
            .retrieve()
            .body(SubmitJobResponse::class.java)!!
            .jobId
    }

    private fun jobSnapshot(jobId: String): Map<*, *> =
        rest.get().uri("http://localhost:$port/jobs/$jobId").retrieve().body(Map::class.java)!!

    @Test
    fun `happy path streams progress and completes`() {
        val session = stompSession()
        val jobId = submit("happy-job")
        val events = session.collect(Destinations.job(jobId))

        val terminal = pollUntilTerminal(events)
        terminal.status.name shouldBe "COMPLETED"
        terminal.progress shouldBe 100
        jobSnapshot(jobId)["status"] shouldBe "COMPLETED"
    }

    @Test
    fun `forced failure produces a FAILED event with error`() {
        val session = stompSession()
        val jobId = submit("doomed-job", failureRate = 1.0)
        val events = session.collect(Destinations.job(jobId))

        val terminal = pollUntilTerminal(events)
        terminal.status.name shouldBe "FAILED"
        (terminal.error != null) shouldBe true
        jobSnapshot(jobId)["status"] shouldBe "FAILED"
    }

    @Test
    fun `state is recoverable without a live subscriber`() {
        val jobId = submit("background-job")

        // Never subscribe to STOMP; poll the REST snapshot until terminal.
        var status = ""
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline && status != "COMPLETED") {
            status = jobSnapshot(jobId)["status"] as String
            Thread.sleep(250)
        }
        status shouldBe "COMPLETED"
    }

    @Test
    fun `firehose receives events for concurrent jobs`() {
        val session = stompSession()
        val all = session.collect(Destinations.JOBS_FIREHOSE)

        val a = submit("job-a")
        val b = submit("job-b")

        val deadline = System.currentTimeMillis() + 15_000
        val seen = mutableSetOf<String>()
        while (System.currentTimeMillis() < deadline && !seen.containsAll(setOf(a, b))) {
            all.poll(500, TimeUnit.MILLISECONDS)?.let { seen.add(it.jobId) }
        }
        seen.containsAll(setOf(a, b)) shouldBe true
    }

    private fun pollUntilTerminal(events: LinkedBlockingDeque<JobProgressEvent>): JobProgressEvent {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val event = events.poll(1, TimeUnit.SECONDS) ?: continue
            if (event.status.isTerminal) return event
        }
        throw AssertionError("No terminal event received in time")
    }
}
```

> `JobStatus.isTerminal` is referenced here — it was defined in Task 2. `Destinations` comes from Task 1.

- [ ] **Step 2: Run the full suite to confirm these fail first, then pass once infra is correct**

Run: `./gradlew test --tests "io.github.rbleuse.playground.JobMonitorIntegrationTest"`
Expected: PASS (4 tests). If a test times out, check container logs — most likely the Pulsar schema customizer (Task 7 Step 2) or the listener container (Task 6 Step 4) is missing.

- [ ] **Step 3: Run the entire test suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`, all tests green (JobSimulatorTest, JobStoreIntegrationTest, JobMonitorIntegrationTest).

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/io/github/rbleuse/playground/JobMonitorIntegrationTest.kt
git commit -m "test: add end-to-end integration tests for the job pipeline"
```

---

## Task 11: Local run verification, cluster compose, and README

**Files:**
- Create: `compose-cluster.yaml`
- Create: `nginx/nginx.conf`
- Create: `README.md`
- Modify: delete `HELP.md` (superseded)

- [ ] **Step 1: Manually verify the dev-time run**

Run: `./gradlew bootRun`
Expected: Boot runs `docker compose up`, starts Valkey + Pulsar, logs `This instance id is app-xxxx`, app listens on `:8080`.

In a second shell:

```bash
curl -i -X POST http://localhost:8080/jobs -H "Content-Type: application/json" -d '{"name":"demo","durationMs":3000}'
```

Expected: `202 Accepted` with a `Location: /jobs/<uuid>` header and `{"jobId":"<uuid>"}` body. Then:

```bash
curl http://localhost:8080/jobs/<uuid>
```

Expected: within ~3s, JSON with `"status":"COMPLETED"` and `"progress":100`. Stop with Ctrl+C.

- [ ] **Step 2: Create the nginx config for the cluster demo**

```
events {}
http {
    upstream app {
        server app1:8080;
        server app2:8080;
    }
    server {
        listen 8080;
        location / {
            proxy_pass http://app;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
        }
    }
}
```

- [ ] **Step 3: Create `compose-cluster.yaml`**

```yaml
services:
  valkey:
    image: 'valkey/valkey:9.1.0'
  pulsar:
    image: 'apachepulsar/pulsar:4.2.2'
    command: [ 'bin/pulsar', 'standalone' ]
  app1:
    image: 'spring-boot-redis-pub-sub-playground:0.0.1-SNAPSHOT'
    environment:
      SPRING_DATA_REDIS_HOST: valkey
      SPRING_PULSAR_CLIENT_SERVICE_URL: pulsar://pulsar:6650
    depends_on: [ valkey, pulsar ]
  app2:
    image: 'spring-boot-redis-pub-sub-playground:0.0.1-SNAPSHOT'
    environment:
      SPRING_DATA_REDIS_HOST: valkey
      SPRING_PULSAR_CLIENT_SERVICE_URL: pulsar://pulsar:6650
    depends_on: [ valkey, pulsar ]
  nginx:
    image: 'nginx:1.27'
    volumes:
      - './nginx/nginx.conf:/etc/nginx/nginx.conf:ro'
    ports:
      - '8080:8080'
    depends_on: [ app1, app2 ]
```

> The app image is built separately (Step 4). In the cluster, `spring-boot-docker-compose` is **not** active (it is `developmentOnly`, absent from the built image), so connections come from the `SPRING_*` env vars above.

- [ ] **Step 4: Build the app image and run the cluster**

Run: `./gradlew bootBuildImage --imageName=spring-boot-redis-pub-sub-playground:0.0.1-SNAPSHOT`
Then: `docker compose -f compose-cluster.yaml up`
Expected: two app containers each log a distinct `app-xxxx` id. `POST` a job through `http://localhost:8080/jobs`; in the app logs, one instance logs `processing job ...` while the other does not — yet a STOMP client connected through nginx still receives that job's events (cross-instance fan-out). Stop with Ctrl+C.

- [ ] **Step 5: Write `README.md`**

````markdown
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

## Follow-up exercises
- Pulsar nack + dead-letter topic for jobs that should be retried (currently a
  failed job is acked as a valid business outcome).
- External STOMP relay (RabbitMQ) instead of the Valkey bridge — compare the two.
- Phase 2: the Angular 22 frontend.
````

- [ ] **Step 6: Remove the scaffolding HELP.md**

Run: `git rm HELP.md`

- [ ] **Step 7: Commit**

```bash
git add compose-cluster.yaml nginx/nginx.conf README.md
git commit -m "docs: add cluster compose, nginx proxy and README"
```

---

## Self-Review Notes

- **Spec coverage:** REST API (Task 9), Valkey hash + index + TTL (Task 4), Pulsar shared subscription (Task 8), Valkey Pub/Sub publish+subscribe (Task 6), STOMP no-SockJS (Task 7), failure simulation (Tasks 5/8), instance identity (Task 1), docker-compose dev services (Task 0), cluster + nginx (Task 11), Testcontainers + Kotest integration tests for all four spec scenarios (Task 10). All spec sections map to a task.
- **Type consistency:** `JobStatus.isTerminal`, `Job.toEvent()`, `JobProgressEvent`, `Destinations.job()/JOBS_FIREHOSE`, `JobSimulator.totalSteps/progressAt/stepDelayMs/shouldFail`, `ProgressReporter.report()`, `JobStore.save/find/findAll` are defined once and referenced consistently across tasks.
- **Known risk to watch during execution:** the Pulsar JSON schema + Kotlin data class path (Task 7 Step 2 customizer). If end-to-end tests fail to deserialize `JobCommand`, that bean is the first place to check.
