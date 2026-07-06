package io.github.rbleuse.playground.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.rbleuse.playground.Channels
import io.github.rbleuse.playground.Destinations
import io.github.rbleuse.playground.model.JobProgressEvent
import org.slf4j.LoggerFactory
import org.springframework.data.redis.annotation.RedisListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class ProgressSubscriber(
    private val messaging: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper,
) {
    @RedisListener(topic = Channels.JOBS_PROGRESS)
    fun onMessage(payload: ByteArray) {
        val event = objectMapper.readValue(payload, JobProgressEvent::class.java)
        logger.debug("Forwarding progress for job {} ({})", event.jobId, event.status)
        messaging.convertAndSend(Destinations.job(event.jobId), event)
        messaging.convertAndSend(Destinations.JOBS_FIREHOSE, event)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProgressSubscriber::class.java)
    }
}
