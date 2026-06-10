package io.github.rbleuse.playground.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring Boot 4 auto-configures a Jackson 3 (`tools.jackson.databind.ObjectMapper`) bean only.
 * Spring Pulsar's [org.springframework.pulsar.core.DefaultSchemaResolver.setObjectMapper] and the
 * Valkey progress pub/sub bridge both require a classic Jackson 2
 * (`com.fasterxml.jackson.databind.ObjectMapper`). Provide one configured for Kotlin data classes
 * and `java.time` types so `JobProgressEvent` (with its `Instant` timestamp) round-trips correctly.
 */
@Configuration
class JacksonConfig {

    @Bean
    fun jackson2ObjectMapper(): ObjectMapper =
        ObjectMapper()
            .registerModule(kotlinModule())
            .registerModule(JavaTimeModule())
}
