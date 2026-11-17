package io.github.rbleuse.playground.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.pulsar.client.api.DeadLetterPolicy
import org.apache.pulsar.spring.core.DefaultSchemaResolver
import org.apache.pulsar.spring.core.SchemaResolver.SchemaResolverCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PulsarConfig {
    // Use Spring's mapper so Pulsar JSON schemas can deserialize Kotlin data classes.
    @Bean
    fun schemaResolverCustomizer(objectMapper: ObjectMapper): SchemaResolverCustomizer<DefaultSchemaResolver> =
        SchemaResolverCustomizer { resolver -> resolver.setObjectMapper(objectMapper) }

    @Bean
    fun jobsDeadLetterPolicy(): DeadLetterPolicy =
        DeadLetterPolicy
            .builder()
            .maxRedeliverCount(5)
            .deadLetterTopic("jobs.submitted.dlq")
            .build()
}
