package io.github.rbleuse.playground.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class JobNotFoundException(
    jobId: String,
) : RuntimeException("Job not found: $jobId")

class JobDispatchException(
    val jobId: String,
    cause: Throwable,
) : RuntimeException("Failed to dispatch job: $jobId", cause)

class JobCancellationException(
    jobId: String,
    status: String,
) : RuntimeException("Job $jobId cannot be cancelled while $status")

@RestControllerAdvice
class JobExceptionHandler {
    @ExceptionHandler(JobNotFoundException::class)
    fun handleNotFound(ex: JobNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Job not found")

    @ExceptionHandler(JobDispatchException::class)
    fun handleDispatchFailure(ex: JobDispatchException): ProblemDetail =
        ProblemDetail
            .forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.message ?: "Failed to dispatch job")
            .apply { setProperty("jobId", ex.jobId) }

    @ExceptionHandler(JobCancellationException::class)
    fun handleCancellationFailure(ex: JobCancellationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Job cannot be cancelled")
}
