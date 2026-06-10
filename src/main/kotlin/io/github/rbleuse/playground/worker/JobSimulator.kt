package io.github.rbleuse.playground.worker

import org.springframework.stereotype.Component

@Component
class JobSimulator(private val steps: Int = 10) {

    val totalSteps: Int get() = steps

    fun progressAt(step: Int): Int = (step * 100) / steps

    fun stepDelayMs(durationMs: Long): Long = durationMs / steps

    fun shouldFail(failureRate: Double, roll: Double): Boolean = roll < failureRate
}
