plugins {
    alias(libs.plugins.kotlin.jvm)
}


sourceSets.main.configure {
    java.srcDir("src/main/fbs_java")
}


dependencies {
    implementation("org.postgresql:postgresql:42.7.3")  // PostgreSQL JDBC driver
    implementation("com.google.flatbuffers:flatbuffers-java:24.3.25")

    implementation("org.locationtech.proj4j:proj4j:1.3.0")
    implementation("org.locationtech.proj4j:proj4j-epsg:1.3.0")

//    implementation("it.unimi.dsi:sux4j:5.4.1")
    implementation("it.unimi.dsi:fastutil:8.5.15")


    implementation(platform("io.arrow-kt:arrow-stack:2.0.1"))
    implementation("io.arrow-kt:arrow-core")
    implementation("io.arrow-kt:arrow-fx-coroutines")

    implementation("com.oracle.database.jdbc:ojdbc11:23.3.0.23.09")
}
