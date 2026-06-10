package io.github.rbleuse.playground.repository

import io.github.rbleuse.playground.RedisKeys
import io.github.rbleuse.playground.model.Job
import io.github.rbleuse.playground.model.JobStatus
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant

@Repository
class JobRepository(
    private val template: StringRedisTemplate,
) {
    fun save(job: Job) {
        val key = RedisKeys.job(job.jobId)
        template.opsForHash<String, String>().putAll(key, job.toHash())
        template.opsForSet().add(RedisKeys.JOB_INDEX, job.jobId)
        if (job.status.isTerminal) {
            template.expire(key, TERMINAL_TTL)
        }
    }

    fun find(jobId: String): Job? {
        val map = template.opsForHash<String, String>().entries(RedisKeys.job(jobId))
        return if (map.isEmpty()) null else map.toJob()
    }

    fun findAll(): List<Job> =
        template
            .opsForSet()
            .members(RedisKeys.JOB_INDEX)
            .orEmpty()
            .mapNotNull { jobId ->
                find(jobId) ?: run {
                    template.opsForSet().remove(RedisKeys.JOB_INDEX, jobId)
                    null
                }
            }

    fun tryAcquireProcessing(
        jobId: String,
        owner: String,
        lease: Duration,
    ): Boolean =
        template
            .opsForValue()
            .setIfAbsent(RedisKeys.processingLock(jobId), owner, lease) == true

    fun releaseProcessing(
        jobId: String,
        owner: String,
    ) {
        template.execute(
            RELEASE_LOCK_SCRIPT,
            listOf(RedisKeys.processingLock(jobId)),
            owner,
        )
    }

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
        private val RELEASE_LOCK_SCRIPT =
            DefaultRedisScript(
                """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                end
                return 0
                """.trimIndent(),
                Long::class.java,
            )
    }
}
