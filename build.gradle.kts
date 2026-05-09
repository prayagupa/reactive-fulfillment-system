// Root build — applies only cross-cutting tasks.
// Each subproject owns its own build.gradle.kts.

plugins {
    base  // gives lifecycle tasks: clean, assemble, check, build
}

// ── Global tasks ──────────────────────────────────────────────────────────────

tasks.register("buildAll") {
    group = "build"
    description = "Build every subproject in dependency order."
    dependsOn(subprojects.map { "${it.path}:build" })
}

tasks.register("testAll") {
    group = "verification"
    description = "Run tests across every subproject."
    dependsOn(subprojects.map { "${it.path}:test" })
}

tasks.register("lintAll") {
    group = "verification"
    description = "Run linters/formatters across every subproject."
    dependsOn(subprojects.mapNotNull { p ->
        p.tasks.findByName("lint")?.let { "${p.path}:lint" }
    })
}

// ── Dependency graph helper ───────────────────────────────────────────────────

tasks.register("graph") {
    group = "help"
    description = "Print subproject dependency graph."
    doLast {
        subprojects.forEach { p ->
            println("${p.path}")
            p.configurations.findByName("implementation")
                ?.dependencies
                ?.filterIsInstance<ProjectDependency>()
                ?.forEach { dep -> println("  └─ :${dep.name}") }
        }
    }
}
