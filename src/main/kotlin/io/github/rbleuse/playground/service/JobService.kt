package io.github.rbleuse.playground.service

import io.github.rbleuse.playground.Topics
import io.github.rbleuse.playground.dto.SubmitJobRequest
import io.github.rbleuse.playground.exception.JobCancellationException
import io.github.rbleuse.playground.exception.JobDispatchException
import io.github.rbleuse.playground.exception.JobNotFoundException
import io.github.rbleuse.playground.model.Job
import io.github.rbleuse.playground.model.JobCommand
import io.github.rbleuse.playground.model.JobStatus
import io.github.rbleuse.playground.repository.JobRepository
import org.slf4j.LoggerFactory
import org.springframework.pulsar.core.PulsarTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class JobService(
    private val repository: JobRepository,
    private val pulsarTemplate: PulsarTemplate<JobCommand>,
    private val reporter: ProgressReporter,
) {
    fun submit(request: SubmitJobRequest): String {
        val jobId = UUID.randomUUID().toString()
        val now = Instant.now()
        val job =
            Job(
                jobId = jobId,
                name = request.name,
                status = if (request.scheduledAt == null) JobStatus.QUEUED else JobStatus.SCHEDULED,
                progress = 0,
                submittedAt = now,
                updatedAt = now,
                scheduledAt = request.scheduledAt,
            )
        reporter.report(job)
        try {
            val command = JobCommand(jobId, request.name, request.durationMs, request.failureRate)
            if (request.scheduledAt == null) {
                pulsarTemplate.send(Topics.JOBS_SUBMITTED, command)
            } else {
                val delayMs = Duration.between(Instant.now(), request.scheduledAt).toMillis().coerceAtLeast(1)
                pulsarTemplate
                    .newMessage(command)
                    .withTopic(Topics.JOBS_SUBMITTED)
                    .withMessageCustomizer { it.deliverAfter(delayMs, TimeUnit.MILLISECONDS) }
                    .send()
            }
        } catch (ex: RuntimeException) {
            reporter.report(
                job.copy(
                    status = JobStatus.FAILED,
                    updatedAt = Instant.now(),
                    error = "Failed to dispatch job: ${ex.message ?: ex.javaClass.simpleName}",
                ),
            )
            throw JobDispatchException(jobId, ex)
        }
        logger.info("{} job {} ({})", job.status, jobId, request.name)
        return jobId
    }

    fun cancel(jobId: String): Job {
        when (val blockedBy = repository.tryCancel(jobId, Instant.now())) {
            null -> {}

            "" -> {
                throw JobNotFoundException(jobId)
            }

            else -> {
                throw JobCancellationException(jobId, blockedBy)
            }
        }
        // Re-read so the reported job reflects the atomic transition, not the pre-cancel snapshot.
        return get(jobId).also(reporter::report)
    }

    fun get(jobId: String): Job = repository.find(jobId) ?: throw JobNotFoundException(jobId)

    fun list(): List<Job> = repository.findAll()

    companion object {
        private val logger = LoggerFactory.getLogger(JobService::class.java)
    }
}
