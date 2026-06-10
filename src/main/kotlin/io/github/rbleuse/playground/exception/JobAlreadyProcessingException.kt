package io.github.rbleuse.playground.exception

class JobAlreadyProcessingException(
    jobId: String,
) : RuntimeException("Job is already being processed: $jobId")
