plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("com.oracle.database.jdbc:ojdbc11:23.9.0.25.07")
    implementation("com.oracle.database.xml:xdb:23.9.0.25.07")
}

fun getDbProperty(project: Project, propertyName: String): String {
    return project.findProperty(propertyName)?.toString()
        ?: System.getenv(propertyName)
        ?: error("Please set $propertyName in gradle.properties or as an environment variable")
}

fun JavaExec.configureDbProperties() {
    val matrikkelDbUrl = getDbProperty(project, "MATRIKKEL_DB_URL")
    val matrikkelDbUsername = getDbProperty(project, "MATRIKKEL_DB_USERNAME")
    val matrikkelDbPassword = getDbProperty(project, "MATRIKKEL_DB_PASSWORD")
    val arbeidslisteDbUrl = getDbProperty(project, "ARBEIDSLISTE_DB_URL")
    val arbeidslisteDbUsername = getDbProperty(project, "ARBEIDSLISTE_DB_USERNAME")
    val arbeidslisteDbPassword = getDbProperty(project, "ARBEIDSLISTE_DB_PASSWORD")

    environment("MATRIKKEL_DB_URL", matrikkelDbUrl)
    environment("MATRIKKEL_DB_USERNAME", matrikkelDbUsername)
    environment("MATRIKKEL_DB_PASSWORD", matrikkelDbPassword)
    environment("ARBEIDSLISTE_DB_URL", arbeidslisteDbUrl)
    environment("ARBEIDSLISTE_DB_USERNAME", arbeidslisteDbUsername)
    environment("ARBEIDSLISTE_DB_PASSWORD", arbeidslisteDbPassword)

    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
}

tasks.register<JavaExec>("runBulkImportAll") {
    configureDbProperties()
    description = "Import all raw data with consistent snapshot endringsnummer"
    mainClass.set("no.kartverket.nibas.download.BulkImportAllKt")
}

tasks.register<JavaExec>("runVerifyImport") {
    configureDbProperties()
    description = "Verify import by comparing row counts between Oracle and PostgreSQL"
    mainClass.set("no.kartverket.nibas.download.VerifyImportKt")
}
