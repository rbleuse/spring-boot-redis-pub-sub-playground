package io.github.rbleuse.playground.controller

import io.github.rbleuse.playground.dto.SubmitJobRequest
import io.github.rbleuse.playground.dto.SubmitJobResponse
import io.github.rbleuse.playground.model.Job
import io.github.rbleuse.playground.service.JobService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/jobs")
class JobController(
    private val service: JobService,
) {
    @PostMapping
    fun submit(
        @Valid @RequestBody request: SubmitJobRequest,
    ): ResponseEntity<SubmitJobResponse> {
        val jobId = service.submit(request)
        return ResponseEntity
            .accepted()
            .location(URI.create("/jobs/$jobId"))
            .body(SubmitJobResponse(jobId))
    }

    @GetMapping("/{jobId}")
    fun get(
        @PathVariable jobId: String,
    ): Job = service.get(jobId)

    @PostMapping("/{jobId}/cancel")
    fun cancel(
        @PathVariable jobId: String,
    ): Job = service.cancel(jobId)

    @GetMapping
    fun list(): List<Job> = service.list()
}
