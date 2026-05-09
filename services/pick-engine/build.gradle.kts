plugins {
    id("java-service-conventions")
}

group = "com.shipping"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.websocket)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.kafka)
    implementation(libs.confluent.avro.serializer)
    implementation(libs.cassandra.driver.core)
    implementation(libs.micrometer.prometheus)
    implementation(libs.otel.spring.starter)
    implementation(project(":libs:common-events"))
    implementation(project(":libs:common-kafka"))

    testImplementation(libs.spring.boot.test)
    testImplementation(libs.testcontainers.kafka)
}
