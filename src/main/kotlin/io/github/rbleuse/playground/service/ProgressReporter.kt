package io.github.rbleuse.playground.service

import io.github.rbleuse.playground.messaging.ProgressPublisher
import io.github.rbleuse.playground.model.Job
import io.github.rbleuse.playground.repository.JobRepository
import org.springframework.stereotype.Service

@Service
class ProgressReporter(
    private val repository: JobRepository,
    private val publisher: ProgressPublisher,
) {
    /** Persist the new snapshot then broadcast it. */
    fun report(job: Job) {
        repository.save(job)
        publisher.publish(job.toEvent())
    }
}
