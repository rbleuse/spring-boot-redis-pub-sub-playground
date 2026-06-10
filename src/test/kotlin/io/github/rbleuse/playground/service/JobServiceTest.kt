package io.github.rbleuse.playground.service

import io.github.rbleuse.playground.Topics
import io.github.rbleuse.playground.dto.SubmitJobRequest
import io.github.rbleuse.playground.model.Job
import io.github.rbleuse.playground.model.JobCommand
import io.github.rbleuse.playground.model.JobStatus
import io.github.rbleuse.playground.repository.JobRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.pulsar.core.PulsarTemplate

class JobServiceTest {
    private val store = mockk<JobRepository>(relaxUnitFun = true)
    private val pulsarTemplate = mockk<PulsarTemplate<JobCommand>>()
    private val service = JobService(store, pulsarTemplate)

    @Test
    fun `dispatch failure marks the submitted job as failed`() {
        every {
            pulsarTemplate.send(Topics.JOBS_SUBMITTED, any<JobCommand>())
        } throws IllegalStateException("broker unavailable")

        assertThrows<RuntimeException> {
            service.submit(SubmitJobRequest(name = "test-job"))
        }

        val jobs = mutableListOf<Job>()
        verify(exactly = 2) { store.save(capture(jobs)) }
        jobs[0].status shouldBe JobStatus.QUEUED
        jobs[1].status shouldBe JobStatus.FAILED
        jobs[1].error shouldBe "Failed to dispatch job: broker unavailable"
    }
}
