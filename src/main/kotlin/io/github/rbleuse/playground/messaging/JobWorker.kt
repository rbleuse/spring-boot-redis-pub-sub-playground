package io.github.rbleuse.playground.messaging

import io.github.rbleuse.playground.model.JobCommand
import io.github.rbleuse.playground.service.JobProcessor
import org.apache.pulsar.client.api.SubscriptionType
import org.apache.pulsar.common.schema.SchemaType
import org.springframework.pulsar.annotation.PulsarListener
import org.springframework.stereotype.Component

@Component
class JobWorker(
    private val processor: JobProcessor,
) {
    @PulsarListener(
        subscriptionName = "job-workers",
        topics = ["jobs.submitted"],
        subscriptionType = [SubscriptionType.Shared],
        schemaType = SchemaType.JSON,
    )
    fun onJobSubmitted(command: JobCommand) {
        processor.process(command)
    }
}
