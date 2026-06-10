package io.github.rbleuse.playground.config

import io.github.rbleuse.playground.Channels
import io.github.rbleuse.playground.progress.ProgressSubscriber
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer

@Configuration
class RedisListenerConfig {
    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
        subscriber: ProgressSubscriber,
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        container.addMessageListener(subscriber, ChannelTopic(Channels.JOBS_PROGRESS))
        return container
    }
}
