package io.github.rbleuse.playground.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.rbleuse.playground.Channels
import io.github.rbleuse.playground.model.JobProgressEvent
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class ProgressPublisher(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun publish(event: JobProgressEvent) {
        redis.convertAndSend(Channels.JOBS_PROGRESS, objectMapper.writeValueAsString(event))
    }
}
