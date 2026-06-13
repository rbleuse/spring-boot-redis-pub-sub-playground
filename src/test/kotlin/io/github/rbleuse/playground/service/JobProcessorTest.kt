package io.github.rbleuse.playground.service

import io.github.rbleuse.playground.model.InstanceInfo
import io.github.rbleuse.playground.model.Job
import io.github.rbleuse.playground.model.JobCommand
import io.github.rbleuse.playground.model.JobStatus
import io.github.rbleuse.playground.repository.JobRepository
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.random.Random

class JobProcessorTest {
    private val store = mockk<JobRepository>(relaxUnitFun = true)
    private val reporter = mockk<ProgressReporter>(relaxUnitFun = true)
    private val random = mockk<Random>()
    private val processor =
        JobProcessor(
            repository = store,
            reporter = reporter,
            simulator = JobSimulator(steps = 2),
            instance = InstanceInfo("app-test"),
            random = random,
        )

    @Test
    fun `terminal jobs are not processed again`() {
        val command = command()
        every { store.find(command.jobId) } returns job(status = JobStatus.COMPLETED, progress = 100)

        processor.process(command)

        verify { reporter wasNot Called }
        verify { random wasNot Called }
    }

    @Test
    fun `cancelled delayed jobs are acknowledged without processing`() {
        val command = command()
        every { store.find(command.jobId) } returns job(status = JobStatus.CANCELLED)

        processor.process(command)

        verify { reporter wasNot Called }
        verify { random wasNot Called }
    }

    @Test
    fun `jobs without stored state are skipped, never fabricated`() {
        val command = command()
        every { store.find(command.jobId) } returns null

        processor.process(command)

        verify { reporter wasNot Called }
        verify(exactly = 0) { store.tryAcquireProcessing(any(), any(), any()) }
    }

    @Test
    fun `failure preserves progress from completed steps`() {
        val command = command(failureRate = 0.5)
        val lockOwner = slot<String>()
        every { store.find(command.jobId) } returns job()
        every { store.tryAcquireProcessing(command.jobId, capture(lockOwner), any()) } returns true
        every { store.tryStart(command.jobId, any()) } returns true
        every { random.nextDouble() } returnsMany listOf(1.0, 0.0)

        processor.process(command)

        val jobs = mutableListOf<Job>()
        verify(exactly = 2) { reporter.report(capture(jobs)) }
        jobs[0].status shouldBe JobStatus.RUNNING
        jobs[0].progress shouldBe 50
        jobs[1].status shouldBe JobStatus.FAILED
        jobs[1].progress shouldBe 50
        lockOwner.captured.startsWith("app-test:") shouldBe true
        verify { store.releaseProcessing(command.jobId, lockOwner.captured) }
    }

    private fun command(failureRate: Double = 0.0) =
        JobCommand(
            jobId = "job-1",
            name = "test-job",
            durationMs = 0,
            failureRate = failureRate,
        )

    private fun job(
        status: JobStatus = JobStatus.QUEUED,
        progress: Int = 0,
    ) = Job(
        jobId = "job-1",
        name = "test-job",
        status = status,
        progress = progress,
        submittedAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
