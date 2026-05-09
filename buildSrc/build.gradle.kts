plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    // Spring Boot 4 Gradle plugin — needed so convention plugins can apply it
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.0.0")
}
