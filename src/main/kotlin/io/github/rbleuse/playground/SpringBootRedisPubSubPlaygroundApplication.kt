package io.github.rbleuse.playground

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringBootRedisPubSubPlaygroundApplication

fun main(args: Array<String>) {
	runApplication<SpringBootRedisPubSubPlaygroundApplication>(*args)
}
