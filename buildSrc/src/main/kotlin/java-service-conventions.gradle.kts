/**
 * Convention plugin: java-service-conventions
 *
 * Apply to every Spring Boot 4 service under /services/.
 * Provides:
 *  - Java 21 toolchain
 *  - Spring Boot plugin (executable fat JAR)
 *  - JUnit 5 test runner
 *  - Testcontainers support
 *  - Docker image build task (docker build -t <image>:<sha> .)
 */
plugins {
    java
    id("org.springframework.boot")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:unchecked"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
    // Allow Testcontainers to reuse containers across test runs in local dev
    systemProperty("testcontainers.reuse.enable", "true")
}

// ── Docker build task ─────────────────────────────────────────────────────────

val imageTag: String by lazy {
    System.getenv("IMAGE_TAG")
        ?: "dev-${project.version}"
}

val registry: String by lazy {
    System.getenv("REGISTRY") ?: "harbor.shipping.internal"
}

tasks.register<Exec>("dockerBuild") {
    group = "docker"
    description = "Build Docker image for this service."
    dependsOn(tasks.named("bootJar"))
    commandLine(
        "docker", "build",
        "-t", "$registry/${project.name}:$imageTag",
        "."
    )
    workingDir = projectDir
}

tasks.register<Exec>("dockerPush") {
    group = "docker"
    description = "Push Docker image to registry."
    dependsOn(tasks.named("dockerBuild"))
    commandLine(
        "docker", "push",
        "$registry/${project.name}:$imageTag"
    )
}
