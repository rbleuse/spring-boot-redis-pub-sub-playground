package io.github.rbleuse.playground.instance

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.random.Random

data class InstanceInfo(val id: String)

@Configuration
class InstanceConfig {
    @Bean
    fun instanceInfo(): InstanceInfo {
        val id = "app-%04x".format(Random.nextInt(0, 0x10000))
        logger.info("This instance id is {}", id)
        return InstanceInfo(id)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InstanceConfig::class.java)
    }
}
