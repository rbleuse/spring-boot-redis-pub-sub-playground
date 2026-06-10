package io.github.rbleuse.playground.job

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

    @GetMapping
    fun list(): List<Job> = service.list()
}
