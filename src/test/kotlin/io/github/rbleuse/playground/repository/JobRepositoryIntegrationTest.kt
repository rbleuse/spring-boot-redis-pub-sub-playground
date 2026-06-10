package io.github.rbleuse.playground.repository

import io.github.rbleuse.playground.RedisKeys
import io.github.rbleuse.playground.model.Job
import io.github.rbleuse.playground.model.JobStatus
import io.github.rbleuse.playground.support.IntegrationTest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

class JobRepositoryIntegrationTest
    @Autowired
    constructor(
        private val repository: JobRepository,
        private val template: StringRedisTemplate,
    ) : IntegrationTest() {
        private fun newJob(status: JobStatus = JobStatus.QUEUED) =
            Job(
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
            repository.save(job)

            val found = repository.find(job.jobId)

            found.shouldNotBeNull()
            found.jobId shouldBe job.jobId
            found.name shouldBe "test-job"
            found.status shouldBe JobStatus.QUEUED
        }

        @Test
        fun `find returns null for unknown job`() {
            repository.find("does-not-exist").shouldBeNull()
        }

        @Test
        fun `findAll lists saved jobs`() {
            val job = newJob()
            repository.save(job)

            repository.findAll().map { it.jobId } shouldContain job.jobId
        }

        @Test
        fun `findAll removes index entries whose hashes have expired`() {
            val job = newJob(status = JobStatus.COMPLETED)
            repository.save(job)
            template.delete(RedisKeys.job(job.jobId))

            repository.findAll()

            template.opsForSet().isMember(RedisKeys.JOB_INDEX, job.jobId) shouldBe false
        }

        @Test
        fun `processing lease can only be released by its owner`() {
            val jobId = UUID.randomUUID().toString()

            repository.tryAcquireProcessing(jobId, "owner-a", Duration.ofMinutes(1)) shouldBe true
            repository.tryAcquireProcessing(jobId, "owner-b", Duration.ofMinutes(1)) shouldBe false

            repository.releaseProcessing(jobId, "owner-b")
            repository.tryAcquireProcessing(jobId, "owner-b", Duration.ofMinutes(1)) shouldBe false

            repository.releaseProcessing(jobId, "owner-a")
            repository.tryAcquireProcessing(jobId, "owner-b", Duration.ofMinutes(1)) shouldBe true
            repository.releaseProcessing(jobId, "owner-b")
        }
    }
