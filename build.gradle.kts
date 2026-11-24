plugins {
	kotlin("jvm") version "2.4.21"
	kotlin("plugin.spring") version "2.4.21"
	id("org.springframework.boot") version "4.2.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.jmailen.kotlinter") version "5.7.0"
}

group = "io.github.rbleuse"
version = "0.0.1-SNAPSHOT"

extra["pulsar.version"] = "5.0.1"
extra["netty.version"] = "4.2.20.Final"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
	// shortcut: keep ktlint 1.8.0's compiler out of Boot's alignment; revisit on kotlinter upgrades.
	add("ktlint", "org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.21")

	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.apache.pulsar:pulsar-spring-boot-starter:3.0.0-M1")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-jackson2")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	runtimeOnly("com.julien-dubois.bootui:bootui-spring-boot-starter:2.5.0")

	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-pulsar")
	testImplementation("io.kotest:kotest-assertions-core:6.2.7")
	testImplementation("com.ninja-squad:springmockk:5.0.1")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
