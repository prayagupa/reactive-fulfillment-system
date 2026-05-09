plugins {
    id("java-lib-conventions")
}

group = "com.shipping"
version = "1.0.0-SNAPSHOT"

dependencies {
    api(libs.spring.boot.autoconfigure)
    api(libs.spring.boot.web)
    api(libs.spring.boot.security)
    api(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    testImplementation(libs.spring.boot.test)
}
