package no.kartverket.nibas.buildtool

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

abstract class CustomizeMatrikkelApiWsdl : DefaultTask() {
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    protected fun customizeWsdl() {
        val destDir = outputDir.get().asFile
        val sources = inputDir.get().asFile
        destDir.listFiles()?.forEach { it.deleteRecursively() }
        destDir.mkdirs()
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val docBuilder = DocumentBuilderFactory.newInstance().run {
            isNamespaceAware = true
            isIgnoringComments = true
            isIgnoringElementContentWhitespace = true
            newDocumentBuilder()
        }
        val transfomer = TransformerFactory.newInstance().newTransformer()

        sources
            .walk()
            .filter { it.extension == "xsd" }
            .map { it.relativeTo(sources) to docBuilder.parse(it) }
            .forEach { (file, doc) ->
                val root = doc.documentElement

                val jakartaJaxbVersion = root.attributes.getNamedItemNS("https://jakarta.ee/xml/ns/jaxb", "version")
                val jaxbNs =
                    if (jakartaJaxbVersion != null) "https://jakarta.ee/xml/ns/jaxb" else "http://java.sun.com/xml/ns/jaxb"

                // "http://java.sun.com/xml/ns/jaxb" || node.namespaceURI == "https://jakarta.ee/xml/ns/jaxb")) {
                val targetNamespace = URI.create(root.getAttribute("targetNamespace"))
                val packageName = "no.kartverket.nibas.matrikkel.api." + targetNamespace.path.replace(Regex("/+"), ".")
                    .removePrefix(".matrikkelapi.wsapi.v1.")
                var annotationElement: Node? = null
                for (i in 0 until root.childNodes.length) {
                    val annotation = root.childNodes.item(i)
                    if (annotation.localName == "annotation" && annotation.namespaceURI == "http://www.w3.org/2001/XMLSchema") {
                        annotationElement = annotation
                        break
                    }
                }
                if (annotationElement == null) {
                    annotationElement = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "annotation")!!
                    root.appendChild(annotationElement)
                }
                var appinfoElement: Node? = null
                for (i in 0 until annotationElement.childNodes.length) {
                    val node = annotationElement.childNodes.item(i)
                    if (node.localName == "appinfo" && node.namespaceURI == "http://www.w3.org/2001/XMLSchema") {
                        appinfoElement = node
                        break
                    }
                }
                if (appinfoElement == null) {
                    appinfoElement = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "appinfo")!!
                    annotationElement.appendChild(appinfoElement)
                }
                var schemaBindingsElement: Node? = null
                for (i in 0 until appinfoElement.childNodes.length) {
                    val node = appinfoElement.childNodes.item(i)
                    if (node.localName == "schemaBindings" && (node.namespaceURI == "http://java.sun.com/xml/ns/jaxb" || node.namespaceURI == "https://jakarta.ee/xml/ns/jaxb")) {
                        schemaBindingsElement = node
                        break
                    }
                }
                if (schemaBindingsElement == null) {
                    schemaBindingsElement = doc.createElementNS(jaxbNs, "schemaBindings")!!
                    appinfoElement.appendChild(schemaBindingsElement)
                }
                var packageElement: Node? = null
                for (i in 0 until schemaBindingsElement.childNodes.length) {
                    val node = schemaBindingsElement.childNodes.item(i)
                    if (node.localName == "package" && (node.namespaceURI == "http://java.sun.com/xml/ns/jaxb" || node.namespaceURI == "https://jakarta.ee/xml/ns/jaxb")) {
                        packageElement = node
                        break
                    }
                }
                if (packageElement == null) {
                    packageElement = doc.createElementNS(jaxbNs, "package")!!
                    schemaBindingsElement.appendChild(packageElement)
                }
                packageElement.attributes.setNamedItem(doc.createAttribute("name").apply { value = packageName })
                val outputFile = destDir.resolve(file)
                outputFile.parentFile?.mkdirs()
                transfomer.transform(DOMSource(doc), StreamResult(outputFile))
            }

        sources.walk()
            .filter { it.extension == "wsdl" }
            .forEach {
                it.copyTo(destDir.resolve(it.relativeTo(sources)), true)
            }
    }

}

