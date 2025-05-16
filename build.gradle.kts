allprojects {
    group = "no.kartverket.nibas"
    version = "0.0.1-SNAPSHOT"
}

plugins {
    alias(libs.plugins.kotlin.jvm)
}

subprojects {
    repositories {
        mavenCentral()
    }
}
