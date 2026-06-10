package io.github.rbleuse.playground.progress

import io.github.rbleuse.playground.job.JobStatus
import java.time.Instant

data class JobProgressEvent(
    val jobId: String,
    val name: String,
    val status: JobStatus,
    val progress: Int,
    val workerId: String?,
    val error: String?,
    val timestamp: Instant,
)
