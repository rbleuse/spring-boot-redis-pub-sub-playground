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
