plugins {
    alias(libs.plugins.kotlin.jvm)
}


dependencies {
    implementation("org.postgresql:postgresql:42.7.3")  // PostgreSQL JDBC driver
    implementation("com.google.flatbuffers:flatbuffers-java:25.2.10")

    implementation("org.locationtech.proj4j:proj4j:1.3.0")
    implementation("org.locationtech.proj4j:proj4j-epsg:1.3.0")

//    implementation("it.unimi.dsi:sux4j:5.4.1")
    implementation("it.unimi.dsi:fastutil:8.5.15")


    implementation(platform("io.arrow-kt:arrow-stack:2.0.1"))
    implementation("io.arrow-kt:arrow-core")
    implementation("io.arrow-kt:arrow-fx-coroutines")

    implementation("com.oracle.database.jdbc:ojdbc11:23.3.0.23.09")
}

val fbsSchemaDir = file("src/main/fbs")
// Change to the base directory - flatc will create the full package structure
val fbsGeneratedBaseDir = file("src/main/fbs_java")

// Task som generer Java/Kotlin files fra FlatBuffe schemas
tasks.register<Exec>("generateFlatbuffers") {
    group = "build"
    description = "Generere Java files fra FlatBuffer schemas med flatc"

    doFirst {
        if (!isFlatcInstalled()) {
            throw GradleException("""
                flatc er ikke installert eller i path
                Se: https://github.com/google/flatbuffers
            """.trimIndent())
        }
        fbsGeneratedBaseDir.mkdirs()
    }

    outputs.dir(fbsGeneratedBaseDir)
    inputs.dir(fbsSchemaDir)

    commandLine(
        "flatc",
        "--java",
        // Add package prefix to ensure files are generated with the full package structure
        "--java-package-prefix", "no.kartverket.nibas.flatbuffer",
        "-o", fbsGeneratedBaseDir.absolutePath,
        "--filename-suffix", "Fb", // Optional: to avoid name clashes, e.g. MatrikkelDBFb.java
        file("$fbsSchemaDir/MatrikkelDB.fbs").absolutePath,
        file("$fbsSchemaDir/MatrikkelDB.Kode.fbs").absolutePath,
        file("$fbsSchemaDir/Nibas.fbs").absolutePath,
        file("$fbsSchemaDir/Nibas.Common.fbs").absolutePath
    )
}

fun isFlatcInstalled(): Boolean {
    return try {
        val process = ProcessBuilder("flatc", "--version").start()
        process.waitFor(5, TimeUnit.SECONDS)
        process.exitValue() == 0
    } catch (e: Exception) {
        false
    }
}


sourceSets.main.configure {
    java.srcDir(fbsGeneratedBaseDir)
}

tasks.register<JavaExec>("runMatrikkelDownload") {
    val matrikkelDbUrl: String = project
        .findProperty("MATRIKKEL_DB_URL")?.toString()
        ?: System.getenv("MATRIKKEL_DB_URL")
        ?: error("Please set MATRIKKEL_DB_URL in gradle.properties or env")

    val matrikkelDbUsername: String = project
        .findProperty("MATRIKKEL_DB_USERNAME")?.toString()
        ?: System.getenv("MATRIKKEL_DB_USERNAME")
        ?: error("Please set MATRIKKEL_DB_USERNAME")

    val matrikkelDbPassword: String = project
        .findProperty("MATRIKKEL_DB_PASSWORD")?.toString()
        ?: System.getenv("MATRIKKEL_DB_PASSWORD")
        ?: error("Please set MATRIKKEL_DB_PASSWORD")

    environment("MATRIKKEL_DB_URL", matrikkelDbUrl)
    environment("MATRIKKEL_DB_USERNAME", matrikkelDbUsername)
    environment("MATRIKKEL_DB_PASSWORD", matrikkelDbPassword)
    group = "application"
    description = "Run MatrikkelDownload main"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("no.kartverket.nibas.arblist.matrikkel.download.MatrikkelDownloadKt")
}

tasks.register<JavaExec>("runConvert") {
    group = "application"
    description = "Run RunConvert.kt main"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("no.kartverket.nibas.arblist.matrikkel.download.RunConvertKt")
}

tasks.named("runConvert") {
    mustRunAfter("runMatrikkelDownload")
}

tasks.register("downloadAndConvert") {
    dependsOn("runMatrikkelDownload", "runConvert")
    group = "application"
    description = "Run MatrikkelDownload and conversion in sequence"
}
