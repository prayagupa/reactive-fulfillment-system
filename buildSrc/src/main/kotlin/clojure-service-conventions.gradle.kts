/**
 * Convention plugin: clojure-service-conventions
 *
 * Apply to Clojure services (pack-service).
 * Delegates to Leiningen (lein) which is idiomatic for Clojure projects.
 * Exposes Gradle lifecycle tasks so the monorepo graph can include it.
 */
plugins {
    base
}

// ── compile (lein compile) ────────────────────────────────────────────────────

val compile by tasks.registering(Exec::class) {
    group = "build"
    description = "AOT compile Clojure sources via lein."
    commandLine("lein", "compile")
    workingDir = projectDir
}

// ── test ──────────────────────────────────────────────────────────────────────

val test by tasks.registering(Exec::class) {
    group = "verification"
    description = "Run lein test."
    commandLine("lein", "test")
    workingDir = projectDir
}

// ── uberjar ───────────────────────────────────────────────────────────────────

val uberjar by tasks.registering(Exec::class) {
    group = "build"
    description = "Build standalone uberjar via lein."
    commandLine("lein", "uberjar")
    workingDir = projectDir
    outputs.dir(layout.projectDirectory.dir("target"))
}

// ── Gradle lifecycle wiring ───────────────────────────────────────────────────

tasks.named("assemble") { dependsOn(uberjar) }
tasks.named("check")    { dependsOn(test) }
tasks.named("build")    { dependsOn(uberjar, test) }

// ── docker ────────────────────────────────────────────────────────────────────

val imageTag: String by lazy { System.getenv("IMAGE_TAG") ?: "dev-local" }
val registry: String by lazy { System.getenv("REGISTRY") ?: "harbor.shipping.internal" }

tasks.register<Exec>("dockerBuild") {
    group = "docker"
    description = "Build Docker image for pack-service."
    dependsOn(uberjar)
    commandLine(
        "docker", "build",
        "-t", "$registry/${project.name}:$imageTag",
        "."
    )
    workingDir = projectDir
}
