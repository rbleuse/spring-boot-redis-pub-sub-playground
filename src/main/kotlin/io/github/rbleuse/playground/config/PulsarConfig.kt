package io.github.rbleuse.playground.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.pulsar.core.DefaultSchemaResolver
import org.springframework.pulsar.core.SchemaResolver.SchemaResolverCustomizer

@Configuration
class PulsarConfig {
    /**
     * Pulsar's default JSON schema uses a shaded ObjectMapper without the Kotlin module,
     * which cannot deserialize Kotlin data classes. Point it at Spring's mapper instead.
     */
    @Bean
    fun schemaResolverCustomizer(objectMapper: ObjectMapper): SchemaResolverCustomizer<DefaultSchemaResolver> =
        SchemaResolverCustomizer<DefaultSchemaResolver> { resolver -> resolver.setObjectMapper(objectMapper) }
}
