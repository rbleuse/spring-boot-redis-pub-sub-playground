package io.github.rbleuse.playground.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.GenericContainer
import org.testcontainers.pulsar.PulsarContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
abstract class IntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val pulsar = PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:4.2.2"))

        @Container
        @ServiceConnection(name = "redis")
        @JvmStatic
        val valkey: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("valkey/valkey:9.1.0")).withExposedPorts(6379)
    }
}
