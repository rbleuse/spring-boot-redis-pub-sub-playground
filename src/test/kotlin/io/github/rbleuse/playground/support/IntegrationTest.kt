package io.github.rbleuse.playground.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.GenericContainer
import org.testcontainers.pulsar.PulsarContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class IntegrationTest {
    companion object {
        // Singleton containers: started once for the whole test-run JVM and never stopped by
        // JUnit's @Testcontainers per-class lifecycle. Multiple @SpringBootTest classes share one
        // Spring context cache; if the containers were torn down between classes, contexts that
        // reconnect (Pulsar listener / Redis listener container) would fail with "connection refused".
        @JvmStatic
        @ServiceConnection
        val pulsar: PulsarContainer =
            PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:4.2.2")).apply { start() }

        @JvmStatic
        @ServiceConnection(name = "redis")
        val valkey: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("valkey/valkey:9.1.0"))
                .withExposedPorts(6379)
                .apply { start() }
    }
}
