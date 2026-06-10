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
