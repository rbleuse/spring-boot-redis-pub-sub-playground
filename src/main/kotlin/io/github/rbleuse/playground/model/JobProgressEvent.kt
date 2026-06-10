package io.github.rbleuse.playground.model

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
