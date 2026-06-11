plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(files("../prebuilt/arbeidsliste-matrikkel-api-4.13.1.0.jar"))
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:4.2.2")
    implementation("org.apache.cxf:cxf-rt-transports-http:4.2.2")
}
