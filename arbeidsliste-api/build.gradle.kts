plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.graalvm.native)
    application
}


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}


dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.postgresql)
    implementation(libs.hibernate.spatial)
    implementation(libs.locationtech.jts.core)
    implementation(libs.jackson.datatype.jts)
    implementation(libs.postgis.jdbc)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.micrometer.prometheus)
    implementation(libs.springdoc.openapi.webmvc.api)
    implementation(libs.springdoc.openapi.webmvc.ui)

    // Flyway for database migrations
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)

    // Testing
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Dev tools
    developmentOnly(libs.spring.boot.devtools)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
