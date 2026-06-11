package io.github.rbleuse.playground.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.rbleuse.playground.Channels
import io.github.rbleuse.playground.model.JobProgressEvent
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class ProgressPublisher(
    private val template: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun publish(event: JobProgressEvent) {
        template.convertAndSend(Channels.JOBS_PROGRESS, objectMapper.writeValueAsString(event))
    }
}
