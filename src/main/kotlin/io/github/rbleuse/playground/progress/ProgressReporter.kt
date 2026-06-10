package io.github.rbleuse.playground.progress

import io.github.rbleuse.playground.job.Job
import io.github.rbleuse.playground.job.JobStore
import org.springframework.stereotype.Service

@Service
class ProgressReporter(
    private val store: JobStore,
    private val publisher: ProgressPublisher,
) {
    /** Persist the new snapshot then broadcast it. */
    fun report(job: Job) {
        store.save(job)
        publisher.publish(job.toEvent())
    }
}
