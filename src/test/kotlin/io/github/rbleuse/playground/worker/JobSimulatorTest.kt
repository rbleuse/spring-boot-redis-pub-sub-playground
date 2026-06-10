package io.github.rbleuse.playground.worker

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class JobSimulatorTest {

    private val simulator = JobSimulator(steps = 10)

    @Test
    fun `progress is evenly distributed across steps`() {
        simulator.progressAt(0) shouldBe 0
        simulator.progressAt(5) shouldBe 50
        simulator.progressAt(10) shouldBe 100
    }

    @Test
    fun `step delay divides total duration by steps`() {
        simulator.stepDelayMs(10_000) shouldBe 1_000
    }

    @Test
    fun `shouldFail is true when roll is below failure rate`() {
        simulator.shouldFail(failureRate = 1.0, roll = 0.0) shouldBe true
        simulator.shouldFail(failureRate = 0.0, roll = 0.0) shouldBe false
        simulator.shouldFail(failureRate = 0.5, roll = 0.4) shouldBe true
        simulator.shouldFail(failureRate = 0.5, roll = 0.6) shouldBe false
    }

    @Test
    fun `totalSteps is exposed`() {
        simulator.totalSteps shouldBe 10
    }
}
