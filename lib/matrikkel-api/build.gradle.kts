import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption

plugins {
    `java-library`
    `maven-publish`
}

group = "no.kartverket.nibas"

// Siste versjon med endringer på MatrikkelApi-skjemaet
//
// https://github.com/kartverket/matrikkel/commits/main/server/eksternapi/api/v1/matrikkelapi-v1-endpoints/src/interfaces/java/no/statkart/matrikkel/matrikkelapi/wsapi/v1/service
// https://github.com/kartverket/matrikkel/commits/main/server/eksternapi/api/v1/matrikkelapi-v1-wsschema/src/main/schema
version = "4.13.1.0"

val jaxwsTools = configurations.create("jaxwsTools") {
    isCanBeConsumed = false
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    compileOnlyApi(libs.jakarta.annotation.api)
    api(libs.jakarta.xml.bind.api)
    api(libs.jakarta.xml.ws.api)
    jaxwsTools(libs.cxf.sl4j.simple)
    jaxwsTools("org.jvnet.jaxb:jaxb-plugins:4.0.8")
    jaxwsTools("com.sun.xml.ws:jaxws-tools:4.0.3")
}


val wsdlPath = "no/kartverket/nibas/matrikkel/api/wsdl"

val extractMatrikkelApiWsdls = tasks.register<Sync>("extractMatrikkelApiWsdls") {
    val zipFile = zipTree(project.layout.projectDirectory.file("matrikkelapi-wsdls-${project.version}.zip"))
    destinationDir = layout.buildDirectory.dir("schema").get().asFile
    from(zipFile) {
        into(wsdlPath)
    }
}

val customizeMatrikkelApiWsdls = tasks.register<no.kartverket.nibas.buildtool.CustomizeMatrikkelApiWsdl>("customizeMatrikkelApiWsdls") {
    outputDir.set(project.layout.buildDirectory.dir("schema-customized"))
    inputDir.set(extractMatrikkelApiWsdls.map { it.destinationDir }.get())
    dependsOn(extractMatrikkelApiWsdls)
}


val generateServiceSource = listOf("StoreService", "EndringsloggService").map { serviceName ->
    tasks.register<JavaExec>("generate${serviceName}Source") {
        val wsimportDir = project.layout.buildDirectory.dir("generated/sources/wsdl2java").get().asFile
        val destinationDir = File(wsimportDir, serviceName)
        val bindingFilePath = "$projectDir/src/main/binding/$serviceName.bindings.xml"
        inputs.file(bindingFilePath)
        inputs.dir(customizeMatrikkelApiWsdls.map { it.outputDir })
        outputs.dir(destinationDir)
        classpath = jaxwsTools
        mainClass.set("com.sun.tools.ws.WsImport")
        args(
            "-s", destinationDir,
            "-Xnocompile",
            "-XdisableAuthenticator",
            "-encoding", "UTF-8",
            "-b", bindingFilePath,
            "-B-episode", "$wsimportDir/$serviceName.episode.xml",
            "-wsdllocation", "/$wsdlPath/${serviceName}WS.wsdl",
            "-B-no-header",
            "-B-mark-generated",
            "-B-noDate",
            "-B-Xvalue-constructor",
            "-B-XfixJAXB1058",
            "-B-Xsetters-mode=direct",
            "-B-XsimpleEquals",
            "-B-XsimpleHashCode",
            "-quiet",
            customizeMatrikkelApiWsdls.map { it.outputDir.get().asFile.resolve("$wsdlPath/${serviceName}WS.wsdl").path }.get()
        )
        doFirst {
            destinationDir.listFiles()?.forEach { it.deleteRecursively() }
            destinationDir.mkdirs()
        }
    }
}

val generateServiceSources = tasks.register<Copy>("generateServiceSources") {
    for (taskProvider in generateServiceSource) {
        from(taskProvider.map { it.outputs.files.singleFile })
    }
    destinationDir = project.layout.buildDirectory.dir("generated/sources/wsdl2java/merged").get().asFile
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

val generateVersionInfo = tasks.register("generateVersionInfo") {
    val buildDir = project.layout.buildDirectory.dir("version-info").get().asFile
    val matrikkelApiVersion = project.version.toString()
    inputs.property("matrikkelapiversion", matrikkelApiVersion)
    outputs.dir(buildDir)
    doLast {
        val metaInf = File(buildDir, "META-INF").apply { mkdirs() }
        val versionFile = File(metaInf, "matrikkel-api.properties")
        Files.writeString(
            versionFile.toPath(),
            "matrikkel.api.version = $matrikkelApiVersion\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING)
    }
}

sourceSets.main.configure {
    java.srcDir(generateServiceSources.map { it.outputs.files.singleFile })
    resources.srcDir(customizeMatrikkelApiWsdls.map { it.outputDir })
    resources.srcDir(generateVersionInfo.map { it.outputs.files.singleFile })
}


publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
