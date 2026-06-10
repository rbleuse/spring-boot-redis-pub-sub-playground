package io.github.rbleuse.playground

object Topics {
    const val JOBS_SUBMITTED = "jobs.submitted"
}

object Channels {
    const val JOBS_PROGRESS = "jobs.progress"
}

object Destinations {
    const val JOBS_FIREHOSE = "/topic/jobs"
    fun job(jobId: String) = "/topic/jobs/$jobId"
}

object RedisKeys {
    fun job(jobId: String) = "job:$jobId"
    const val JOB_INDEX = "jobs:index"
}
