package io.github.rbleuse.playground.model

import org.apache.pulsar.common.schema.SchemaType
import org.springframework.pulsar.annotation.PulsarMessage

@PulsarMessage(schemaType = SchemaType.JSON)
data class JobCommand(
    val jobId: String,
    val name: String,
    val durationMs: Long,
    val failureRate: Double,
)
