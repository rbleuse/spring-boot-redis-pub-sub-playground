package io.github.rbleuse.playground.job

import org.apache.pulsar.common.schema.SchemaType
import org.springframework.pulsar.annotation.PulsarMessage

@PulsarMessage(schemaType = SchemaType.JSON)
data class JobCommand(
    val jobId: String,
    val name: String,
    val durationMs: Long,
    val failureRate: Double,
)
