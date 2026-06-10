package io.github.rbleuse.playground.service

import io.github.rbleuse.playground.exception.JobAlreadyProcessingException
import io.github.rbleuse.playground.model.InstanceInfo
import io.github.rbleuse.playground.model.Job
import io.github.rbleuse.playground.model.JobCommand
import io.github.rbleuse.playground.model.JobStatus
import io.github.rbleuse.playground.repository.JobRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

@Service
class JobProcessor(
    private val repository: JobRepository,
    private val reporter: ProgressReporter,
    private val simulator: JobSimulator,
    private val instance: InstanceInfo,
    private val random: Random = Random.Default,
) {
    fun process(command: JobCommand) {
        logger.info("Instance {} processing job {} ({})", instance.id, command.jobId, command.name)
        val existing = repository.find(command.jobId)
        if (existing?.status?.isTerminal == true) {
            logger.info("Skipping terminal job {} ({})", command.jobId, existing.status)
            return
        }
        val lease = Duration.ofMillis(command.durationMs.coerceAtLeast(0)).plus(PROCESSING_LOCK_GRACE)
        val lockOwner = "${instance.id}:${UUID.randomUUID()}"
        if (!repository.tryAcquireProcessing(command.jobId, lockOwner, lease)) {
            throw JobAlreadyProcessingException(command.jobId)
        }

        try {
            var current = repository.find(command.jobId) ?: baseJob(command)
            if (current.status.isTerminal) {
                logger.info("Skipping terminal job {} ({})", command.jobId, current.status)
                return
            }

            for (step in 1..simulator.totalSteps) {
                Thread.sleep(simulator.stepDelayMs(command.durationMs))
                if (simulator.shouldFail(command.failureRate, random.nextDouble())) {
                    reporter.report(
                        current.copy(
                            status = JobStatus.FAILED,
                            workerId = instance.id,
                            error = "Job failed at step $step of ${simulator.totalSteps}",
                            updatedAt = Instant.now(),
                        ),
                    )
                    logger.info("Job {} FAILED at step {}", command.jobId, step)
                    return
                }
                current =
                    current.copy(
                        status = JobStatus.RUNNING,
                        progress = simulator.progressAt(step),
                        workerId = instance.id,
                        updatedAt = Instant.now(),
                    )
                reporter.report(current)
            }

            reporter.report(
                current.copy(
                    status = JobStatus.COMPLETED,
                    progress = 100,
                    workerId = instance.id,
                    updatedAt = Instant.now(),
                ),
            )
            logger.info("Job {} COMPLETED", command.jobId)
        } finally {
            repository.releaseProcessing(command.jobId, lockOwner)
        }
    }

    private fun baseJob(command: JobCommand) =
        Job(
            jobId = command.jobId,
            name = command.name,
            status = JobStatus.QUEUED,
            progress = 0,
            submittedAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    companion object {
        private val PROCESSING_LOCK_GRACE: Duration = Duration.ofSeconds(30)
        private val logger = LoggerFactory.getLogger(JobProcessor::class.java)
    }
}
