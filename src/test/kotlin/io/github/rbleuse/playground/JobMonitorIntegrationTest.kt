package io.github.rbleuse.playground

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.rbleuse.playground.dto.SubmitJobResponse
import io.github.rbleuse.playground.model.JobProgressEvent
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

class JobMonitorIntegrationTest
    @Autowired
    constructor(
        @param:LocalServerPort private val port: Int,
    ) : IntegrationTest() {
        private val rest = RestClient.create()

        private fun stompSession(): StompSession {
            val client = WebSocketStompClient(StandardWebSocketClient())
            // The stock MappingJackson2MessageConverter uses a bare ObjectMapper with no
            // JavaTimeModule, so it cannot deserialize JobProgressEvent.timestamp (an Instant).
            // Register Kotlin + JavaTime modules to match how the server serializes events.
            client.messageConverter =
                MappingJackson2MessageConverter().apply {
                    objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
                }
            return client
                .connectAsync(
                    "ws://localhost:$port/ws",
                    object : StompSessionHandlerAdapter() {},
                ).get(5, TimeUnit.SECONDS)
        }

        private fun StompSession.collect(destination: String): LinkedBlockingDeque<JobProgressEvent> {
            val events = LinkedBlockingDeque<JobProgressEvent>()
            subscribe(
                destination,
                object : StompFrameHandler {
                    override fun getPayloadType(headers: StompHeaders): Type = JobProgressEvent::class.java

                    override fun handleFrame(
                        headers: StompHeaders,
                        payload: Any?,
                    ) {
                        events.add(payload as JobProgressEvent)
                    }
                },
            )
            return events
        }

        private fun submit(
            name: String,
            durationMs: Long = 2000,
            failureRate: Double = 0.0,
        ): String {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val body = mapOf("name" to name, "durationMs" to durationMs, "failureRate" to failureRate)
            return rest
                .post()
                .uri("http://localhost:$port/jobs")
                .headers { it.addAll(headers) }
                .body(body)
                .retrieve()
                .body(SubmitJobResponse::class.java)!!
                .jobId
        }

        private fun jobSnapshot(jobId: String): Map<*, *> =
            rest
                .get()
                .uri("http://localhost:$port/jobs/$jobId")
                .retrieve()
                .body(Map::class.java)!!

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
