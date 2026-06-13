package io.github.rbleuse.playground.model

import java.time.Instant

enum class JobStatus {
    SCHEDULED,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    ;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
}

data class Job(
    val jobId: String,
    val name: String,
    val status: JobStatus,
    val progress: Int,
    val submittedAt: Instant,
    val updatedAt: Instant,
    val scheduledAt: Instant? = null,
    val workerId: String? = null,
    val error: String? = null,
) {
    fun toEvent(): JobProgressEvent =
        JobProgressEvent(
            jobId = jobId,
            name = name,
            status = status,
            progress = progress,
            scheduledAt = scheduledAt,
            workerId = workerId,
            error = error,
            timestamp = updatedAt,
        )
}
