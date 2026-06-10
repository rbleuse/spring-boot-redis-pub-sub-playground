package io.github.rbleuse.playground.job

import io.github.rbleuse.playground.RedisKeys
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant

@Repository
class JobStore(
    private val redis: StringRedisTemplate,
) {
    fun save(job: Job) {
        val key = RedisKeys.job(job.jobId)
        redis.opsForHash<String, String>().putAll(key, job.toHash())
        redis.opsForSet().add(RedisKeys.JOB_INDEX, job.jobId)
        if (job.status.isTerminal) {
            redis.expire(key, TERMINAL_TTL)
        }
    }

    fun find(jobId: String): Job? {
        val map = redis.opsForHash<String, String>().entries(RedisKeys.job(jobId))
        return if (map.isEmpty()) null else map.toJob()
    }

    fun findAll(): List<Job> =
        redis
            .opsForSet()
            .members(RedisKeys.JOB_INDEX)
            .orEmpty()
            .mapNotNull { find(it) }

    private fun Job.toHash(): Map<String, String> =
        buildMap {
            put("jobId", jobId)
            put("name", name)
            put("status", status.name)
            put("progress", progress.toString())
            put("submittedAt", submittedAt.toString())
            put("updatedAt", updatedAt.toString())
            workerId?.let { put("workerId", it) }
            error?.let { put("error", it) }
        }

    private fun Map<String, String>.toJob(): Job =
        Job(
            jobId = getValue("jobId"),
            name = getValue("name"),
            status = JobStatus.valueOf(getValue("status")),
            progress = getValue("progress").toInt(),
            submittedAt = Instant.parse(getValue("submittedAt")),
            updatedAt = Instant.parse(getValue("updatedAt")),
            workerId = this["workerId"],
            error = this["error"],
        )

    companion object {
        private val TERMINAL_TTL: Duration = Duration.ofHours(1)
    }
}
