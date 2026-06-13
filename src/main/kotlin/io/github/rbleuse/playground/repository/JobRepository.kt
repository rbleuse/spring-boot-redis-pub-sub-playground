package io.github.rbleuse.playground.repository

import io.github.rbleuse.playground.RedisKeys
import io.github.rbleuse.playground.model.Job
import io.github.rbleuse.playground.model.JobStatus
import org.springframework.data.redis.core.RedisCallback
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

    fun findAll(): List<Job> {
        val ids =
            template
                .opsForSet()
                .members(RedisKeys.JOB_INDEX)
                .orEmpty()
                .toList()
        if (ids.isEmpty()) return emptyList()
        val hashes =
            template.executePipelined(
                RedisCallback { connection ->
                    ids.forEach { connection.hashCommands().hGetAll(RedisKeys.job(it).toByteArray()) }
                    null
                },
            )
        val stale = mutableListOf<String>()
        val jobs =
            ids.zip(hashes).mapNotNull { (jobId, hash) ->
                @Suppress("UNCHECKED_CAST")
                val map = hash as? Map<String, String>
                if (map.isNullOrEmpty()) {
                    stale += jobId
                    null
                } else {
                    map.toJob()
                }
            }
        if (stale.isNotEmpty()) {
            template.opsForSet().remove(RedisKeys.JOB_INDEX, *stale.toTypedArray())
        }
        return jobs
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

    // RUNNING is accepted so a worker can take over a job whose previous worker died:
    // a live worker is excluded by the processing lock, not by the status.
    fun tryStart(
        jobId: String,
        updatedAt: Instant,
    ): Boolean =
        transition(
            jobId,
            to = JobStatus.RUNNING,
            updatedAt = updatedAt,
            ttl = Duration.ZERO,
            from = arrayOf(JobStatus.SCHEDULED, JobStatus.QUEUED, JobStatus.RUNNING),
        ) == SUCCESS

    /** Returns null on success, "" when the job does not exist, otherwise the status that blocked the cancel. */
    fun tryCancel(
        jobId: String,
        updatedAt: Instant,
    ): String? =
        transition(
            jobId,
            to = JobStatus.CANCELLED,
            updatedAt = updatedAt,
            ttl = TERMINAL_TTL,
            from = arrayOf(JobStatus.SCHEDULED),
        ).takeUnless { it == SUCCESS }

    private fun transition(
        jobId: String,
        to: JobStatus,
        updatedAt: Instant,
        ttl: Duration,
        from: Array<JobStatus>,
    ): String =
        template
            .execute(
                TRANSITION_SCRIPT,
                listOf(RedisKeys.job(jobId)),
                to.name,
                updatedAt.toString(),
                ttl.seconds.toString(),
                *from.map(JobStatus::name).toTypedArray(),
            ).orEmpty()

    private fun Job.toHash(): Map<String, String> =
        buildMap {
            put("jobId", jobId)
            put("name", name)
            put("status", status.name)
            put("progress", progress.toString())
            put("submittedAt", submittedAt.toString())
            put("updatedAt", updatedAt.toString())
            scheduledAt?.let { put("scheduledAt", it.toString()) }
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
            scheduledAt = this["scheduledAt"]?.let(Instant::parse),
            workerId = this["workerId"],
            error = this["error"],
        )

    companion object {
        private val TERMINAL_TTL: Duration = Duration.ofHours(1)
        private const val SUCCESS = "OK"

        // ARGV: [1]=to, [2]=updatedAt, [3]=ttlSeconds ('0' = none), [4..]=accepted from-states.
        // Returns 'OK' on success, '' when the key is missing, otherwise the current status.
        private val TRANSITION_SCRIPT =
            DefaultRedisScript(
                """
                local status = redis.call('hget', KEYS[1], 'status')
                if status == false then
                    return ''
                end
                for i = 4, #ARGV do
                    if status == ARGV[i] then
                        redis.call('hset', KEYS[1], 'status', ARGV[1], 'updatedAt', ARGV[2])
                        if ARGV[3] ~= '0' then
                            redis.call('expire', KEYS[1], ARGV[3])
                        end
                        return 'OK'
                    end
                end
                return status
                """.trimIndent(),
                String::class.java,
            )
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
