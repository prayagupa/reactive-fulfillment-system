/**
 * Convention plugin: python-service-conventions
 *
 * Apply to Python services (wave-planner, future replenishment).
 * Delegates to the existing requirements.txt + uvicorn stack.
 * Gradle tasks wrap pip / pytest / ruff / docker so the monorepo
 * build graph can include Python services alongside Java ones.
 */
plugins {
    base
}

val venv = layout.buildDirectory.dir("venv")
val requirementsFile = layout.projectDirectory.file("requirements.txt")

// ── install ───────────────────────────────────────────────────────────────────

val install by tasks.registering(Exec::class) {
    group = "python"
    description = "Create virtualenv and install dependencies."
    outputs.dir(venv)
    inputs.file(requirementsFile)
    commandLine(
        "bash", "-c",
        "python3 -m venv ${venv.get()} && " +
        "${venv.get()}/bin/pip install -q --upgrade pip && " +
        "${venv.get()}/bin/pip install -q -r ${requirementsFile.asFile}"
    )
}

// ── lint ──────────────────────────────────────────────────────────────────────

val lint by tasks.registering(Exec::class) {
    group = "verification"
    description = "Lint with ruff."
    dependsOn(install)
    commandLine(
        "${venv.get()}/bin/ruff", "check",
        layout.projectDirectory.asFile.absolutePath
    )
}

// ── typecheck ─────────────────────────────────────────────────────────────────

val typecheck by tasks.registering(Exec::class) {
    group = "verification"
    description = "Type-check with pyright."
    dependsOn(install)
    commandLine(
        "${venv.get()}/bin/pyright",
        layout.projectDirectory.asFile.absolutePath
    )
}

// ── test ──────────────────────────────────────────────────────────────────────

val test by tasks.registering(Exec::class) {
    group = "verification"
    description = "Run pytest."
    dependsOn(install)
    commandLine(
        "${venv.get()}/bin/pytest",
        layout.projectDirectory.asFile.absolutePath,
        "--tb=short", "-q"
    )
}

// ── check (Gradle standard lifecycle) ────────────────────────────────────────

tasks.named("check") {
    dependsOn(lint, typecheck, test)
}

tasks.named("assemble") {
    dependsOn(install)
}

// ── docker ────────────────────────────────────────────────────────────────────

val imageTag: String by lazy { System.getenv("IMAGE_TAG") ?: "dev-local" }
val registry: String by lazy { System.getenv("REGISTRY") ?: "harbor.shipping.internal" }

tasks.register<Exec>("dockerBuild") {
    group = "docker"
    description = "Build Docker image."
    commandLine(
        "docker", "build",
        "-t", "$registry/${project.name}:$imageTag",
        "."
    )
    workingDir = projectDir
}
