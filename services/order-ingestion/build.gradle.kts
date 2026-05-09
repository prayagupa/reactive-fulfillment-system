plugins {
    id("java-service-conventions")
}

group = "com.shipping"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(libs.spring.boot.webflux)
    implementation(libs.spring.boot.validation)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.redis.reactive)
    implementation(libs.spring.kafka)
    implementation(libs.confluent.avro.serializer)
    implementation(libs.cassandra.driver.core)
    implementation(libs.cassandra.driver.mapper)
    implementation(libs.micrometer.prometheus)
    implementation(libs.otel.spring.starter)
    implementation(project(":libs:common-events"))
    implementation(project(":libs:common-kafka"))
    implementation(project(":libs:common-security"))
    implementation(project(":libs:common-cqrs"))

    testImplementation(libs.spring.boot.test)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.cassandra)
    testImplementation(libs.pact.junit5)
}
