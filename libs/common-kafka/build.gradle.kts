plugins {
    id("java-lib-conventions")
}

group = "com.shipping"
version = "1.0.0-SNAPSHOT"

dependencies {
    api(libs.spring.boot.autoconfigure)
    api(libs.spring.kafka)
    api(libs.confluent.avro.serializer)
    api(libs.otel.spring.starter)
    api(project(":libs:common-events"))

    testImplementation(libs.spring.boot.test)
    testImplementation(libs.testcontainers.kafka)
}
