package io.github.rbleuse.playground.worker

import io.github.rbleuse.playground.instance.InstanceInfo
import io.github.rbleuse.playground.job.Job
import io.github.rbleuse.playground.job.JobCommand
import io.github.rbleuse.playground.job.JobStatus
import io.github.rbleuse.playground.job.JobStore
import io.github.rbleuse.playground.progress.ProgressReporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.random.Random

@Service
class JobProcessor(
    private val store: JobStore,
    private val reporter: ProgressReporter,
    private val simulator: JobSimulator,
    private val instance: InstanceInfo,
    private val random: Random = Random.Default,
) {
    fun process(command: JobCommand) {
        logger.info("Instance {} processing job {} ({})", instance.id, command.jobId, command.name)
        val base = store.find(command.jobId) ?: baseJob(command)

        for (step in 1..simulator.totalSteps) {
            Thread.sleep(simulator.stepDelayMs(command.durationMs))
            if (simulator.shouldFail(command.failureRate, random.nextDouble())) {
                reporter.report(
                    base.copy(
                        status = JobStatus.FAILED,
                        workerId = instance.id,
                        error = "Job failed at step $step of ${simulator.totalSteps}",
                        updatedAt = Instant.now(),
                    )
                )
                logger.info("Job {} FAILED at step {}", command.jobId, step)
                return
            }
            reporter.report(
                base.copy(
                    status = JobStatus.RUNNING,
                    progress = simulator.progressAt(step),
                    workerId = instance.id,
                    updatedAt = Instant.now(),
                )
            )
        }

        reporter.report(
            base.copy(
                status = JobStatus.COMPLETED,
                progress = 100,
                workerId = instance.id,
                updatedAt = Instant.now(),
            )
        )
        logger.info("Job {} COMPLETED", command.jobId)
    }

    private fun baseJob(command: JobCommand) = Job(
        jobId = command.jobId,
        name = command.name,
        status = JobStatus.QUEUED,
        progress = 0,
        submittedAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    companion object {
        private val logger = LoggerFactory.getLogger(JobProcessor::class.java)
    }
}
