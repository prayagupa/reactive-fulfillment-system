plugins {
    id("java-lib-conventions")
}

group = "com.shipping"
version = "1.0.0-SNAPSHOT"

dependencies {
    api(libs.avro)
    api(libs.confluent.avro.serializer)
}

// ── Avro code generation ──────────────────────────────────────────────────────
// Compiles .avsc schemas → Java using the avro-tools Main class.
// Run: ./gradlew generateAvro   (automatically wired into compileJava)

val avroGenDir = layout.buildDirectory.dir("generated-avro-java")

sourceSets {
    main {
        java.srcDir(avroGenDir)
    }
}

tasks.register<JavaExec>("generateAvro") {
    group = "build"
    description = "Compile Avro schemas (.avsc) to Java sources."
    val schemaDir = layout.projectDirectory.dir("src/main/avro")
    inputs.dir(schemaDir)
    outputs.dir(avroGenDir)
    classpath = configurations.runtimeClasspath.get()
    mainClass.set("org.apache.avro.tool.Main")
    args = listOf("compile", "schema",
        schemaDir.asFile.absolutePath,
        avroGenDir.get().asFile.absolutePath)
}

tasks.named("compileJava") {
    dependsOn("generateAvro")
}
