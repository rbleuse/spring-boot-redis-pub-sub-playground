package io.github.rbleuse.playground.job

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SubmitJobRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 100)
    val name: String,

    @field:Min(1000)
    @field:Max(120_000)
    val durationMs: Long = 10_000,

    @field:DecimalMin("0.0")
    @field:DecimalMax("1.0")
    val failureRate: Double = 0.0,
)

data class SubmitJobResponse(val jobId: String)
