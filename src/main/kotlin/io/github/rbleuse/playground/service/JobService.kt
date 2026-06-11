package io.github.rbleuse.playground.service

import io.github.rbleuse.playground.Topics
import io.github.rbleuse.playground.dto.SubmitJobRequest
import io.github.rbleuse.playground.exception.JobDispatchException
import io.github.rbleuse.playground.exception.JobNotFoundException
import io.github.rbleuse.playground.model.Job
import io.github.rbleuse.playground.model.JobCommand
import io.github.rbleuse.playground.model.JobStatus
import io.github.rbleuse.playground.repository.JobRepository
import org.slf4j.LoggerFactory
import org.springframework.pulsar.core.PulsarTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class JobService(
    private val repository: JobRepository,
    private val pulsarTemplate: PulsarTemplate<JobCommand>,
) {
    fun submit(request: SubmitJobRequest): String {
        val jobId = UUID.randomUUID().toString()
        val now = Instant.now()
        repository.save(
            Job(
                jobId = jobId,
                name = request.name,
                status = JobStatus.QUEUED,
                progress = 0,
                submittedAt = now,
                updatedAt = now,
            ),
        )
        try {
            pulsarTemplate.send(
                Topics.JOBS_SUBMITTED,
                JobCommand(jobId, request.name, request.durationMs, request.failureRate),
            )
        } catch (ex: RuntimeException) {
            repository.save(
                Job(
                    jobId = jobId,
                    name = request.name,
                    status = JobStatus.FAILED,
                    progress = 0,
                    submittedAt = now,
                    updatedAt = Instant.now(),
                    error = "Failed to dispatch job: ${ex.message ?: ex.javaClass.simpleName}",
                ),
            )
            throw JobDispatchException(jobId, ex)
        }
        logger.info("Queued job {} ({})", jobId, request.name)
        return jobId
    }

    fun get(jobId: String): Job = repository.find(jobId) ?: throw JobNotFoundException(jobId)

    fun list(): List<Job> = repository.findAll()

    companion object {
        private val logger = LoggerFactory.getLogger(JobService::class.java)
    }
}
