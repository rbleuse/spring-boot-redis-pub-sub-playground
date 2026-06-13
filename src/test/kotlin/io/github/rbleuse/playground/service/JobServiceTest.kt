package io.github.rbleuse.playground.service

import io.github.rbleuse.playground.Topics
import io.github.rbleuse.playground.dto.SubmitJobRequest
import io.github.rbleuse.playground.exception.JobCancellationException
import io.github.rbleuse.playground.exception.JobNotFoundException
import io.github.rbleuse.playground.model.Job
import io.github.rbleuse.playground.model.JobCommand
import io.github.rbleuse.playground.model.JobStatus
import io.github.rbleuse.playground.repository.JobRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.pulsar.client.api.TypedMessageBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.pulsar.core.PulsarOperations
import org.springframework.pulsar.core.PulsarTemplate
import org.springframework.pulsar.core.TypedMessageBuilderCustomizer
import java.time.Instant
import java.util.concurrent.TimeUnit

class JobServiceTest {
    private val store = mockk<JobRepository>(relaxUnitFun = true)
    private val pulsarTemplate = mockk<PulsarTemplate<JobCommand>>()
    private val reporter = mockk<ProgressReporter>(relaxUnitFun = true)
    private val service = JobService(store, pulsarTemplate, reporter)

    @Test
    fun `dispatch failure marks the submitted job as failed`() {
        every {
            pulsarTemplate.send(Topics.JOBS_SUBMITTED, any<JobCommand>())
        } throws IllegalStateException("broker unavailable")

        assertThrows<RuntimeException> {
            service.submit(SubmitJobRequest(name = "test-job"))
        }

        val jobs = mutableListOf<Job>()
        verify(exactly = 2) { reporter.report(capture(jobs)) }
        jobs[0].status shouldBe JobStatus.QUEUED
        jobs[1].status shouldBe JobStatus.FAILED
        jobs[1].error shouldBe "Failed to dispatch job: broker unavailable"
    }

    @Test
    fun `scheduled jobs use Pulsar deliverAfter`() {
        val builder = mockk<PulsarOperations.SendMessageBuilder<JobCommand>>()
        val customizer = slot<TypedMessageBuilderCustomizer<JobCommand>>()
        every { pulsarTemplate.newMessage(any()) } returns builder
        every { builder.withTopic(Topics.JOBS_SUBMITTED) } returns builder
        every { builder.withMessageCustomizer(capture(customizer)) } returns builder
        every { builder.send() } returns mockk()

        val scheduledAt = Instant.now().plusSeconds(60)
        service.submit(SubmitJobRequest(name = "test-job", scheduledAt = scheduledAt))

        val message = mockk<TypedMessageBuilder<JobCommand>>(relaxed = true)
        customizer.captured.customize(message)
        verify {
            message.deliverAfter(match { it > 0 }, TimeUnit.MILLISECONDS)
            reporter.report(match { it.status == JobStatus.SCHEDULED && it.scheduledAt == scheduledAt })
        }
    }

    @Test
    fun `cancel marks a scheduled job as cancelled`() {
        val job =
            Job(
                jobId = "job-1",
                name = "test-job",
                status = JobStatus.CANCELLED,
                progress = 0,
                submittedAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                scheduledAt = Instant.MAX,
            )
        every { store.tryCancel(job.jobId, any()) } returns null
        every { store.find(job.jobId) } returns job

        val cancelled = service.cancel(job.jobId)

        cancelled.status shouldBe JobStatus.CANCELLED
        verify { reporter.report(cancelled) }
    }

    @Test
    fun `cancel rejects a job that already started`() {
        every { store.tryCancel("job-1", any()) } returns "RUNNING"

        assertThrows<JobCancellationException> { service.cancel("job-1") }
        verify(exactly = 0) { reporter.report(any()) }
    }

    @Test
    fun `cancel of an unknown job reports not found`() {
        every { store.tryCancel("missing", any()) } returns ""

        assertThrows<JobNotFoundException> { service.cancel("missing") }
        verify(exactly = 0) { reporter.report(any()) }
    }
}
