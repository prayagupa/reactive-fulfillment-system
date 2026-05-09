plugins {
    id("java-service-conventions")
}

group = "com.shipping"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.kafka)
    implementation(libs.confluent.avro.serializer)
    implementation(libs.cassandra.driver.core)
    implementation(libs.camel.spring.boot)
    implementation(libs.camel.edi)
    implementation(libs.camel.ftp)
    implementation(libs.micrometer.prometheus)
    implementation(libs.otel.spring.starter)
    implementation(project(":libs:common-events"))
    implementation(project(":libs:common-kafka"))
    implementation(project(":libs:common-cqrs"))

    testImplementation(libs.spring.boot.test)
    testImplementation(libs.testcontainers.kafka)
}
