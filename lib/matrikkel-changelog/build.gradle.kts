plugins {
    kotlin("jvm") version "2.1.0"
}

dependencies {
    implementation(files("../prebuilt/arbeidsliste-matrikkel-api-4.13.1.0.jar"))
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:4.1.0")
    implementation("org.apache.cxf:cxf-rt-transports-http:4.1.0")
}
