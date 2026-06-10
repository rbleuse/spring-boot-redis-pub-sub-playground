package io.github.rbleuse.playground.job

import io.github.rbleuse.playground.progress.JobProgressEvent
import java.time.Instant

enum class JobStatus {
    QUEUED, RUNNING, COMPLETED, FAILED;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED
}

data class Job(
    val jobId: String,
    val name: String,
    val status: JobStatus,
    val progress: Int,
    val submittedAt: Instant,
    val updatedAt: Instant,
    val workerId: String? = null,
    val error: String? = null,
) {
    fun toEvent(): JobProgressEvent = JobProgressEvent(
        jobId = jobId,
        name = name,
        status = status,
        progress = progress,
        workerId = workerId,
        error = error,
        timestamp = updatedAt,
    )
}
