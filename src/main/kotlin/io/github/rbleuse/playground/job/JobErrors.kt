package io.github.rbleuse.playground.job

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class JobNotFoundException(jobId: String) : RuntimeException("Job not found: $jobId")

@RestControllerAdvice
class JobExceptionHandler {

    @ExceptionHandler(JobNotFoundException::class)
    fun handleNotFound(ex: JobNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Job not found")
}
