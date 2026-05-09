pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://packages.confluent.io/maven/")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://packages.confluent.io/maven/")
        mavenLocal()   // picks up locally-installed common-* snapshots
    }
    // gradle/libs.versions.toml is auto-discovered by Gradle 9 as the "libs" catalog
}

rootProject.name = "reactive-shipping-system"

// ── Shared libraries ──────────────────────────────────────────────────────────
include(":libs:common-events")
include(":libs:common-kafka")
include(":libs:common-security")
include(":libs:common-cqrs")

// ── Services ──────────────────────────────────────────────────────────────────
include(":services:order-ingestion")
include(":services:inventory")
include(":services:wave-planner")   // Python — Gradle delegates to pip/uvicorn
include(":services:pick-engine")
include(":services:pack-service")   // Clojure — Gradle delegates to lein
include(":services:carrier-tracking")
