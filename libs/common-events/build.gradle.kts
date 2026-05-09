plugins {
    id("java-lib-conventions")
}

group = "com.shipping"
version = "1.0.0-SNAPSHOT"

// Dedicated configuration that pulls avro-tools (fat-jar with Main class)
val avroTools: Configuration by configurations.creating

dependencies {
    api(libs.avro)
    api(libs.confluent.avro.serializer)
    avroTools(libs.avro.tools)
}

// ── Avro code generation ──────────────────────────────────────────────────────
// Compiles .avsc schemas → Java using the avro-tools Main class.
// Run: gradle generateAvro   (automatically wired into compileJava)

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
    // Use the avroTools configuration — contains the avro-tools fat-jar with Main
    classpath = avroTools
    mainClass.set("org.apache.avro.tool.Main")
    args = listOf("compile", "schema",
        schemaDir.asFile.absolutePath,
        avroGenDir.get().asFile.absolutePath)
}

tasks.named("compileJava") {
    dependsOn("generateAvro")
}
