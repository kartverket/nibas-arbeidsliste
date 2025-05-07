package no.kartverket.nibas.arblist.matrikkel.download

import no.kartverket.nibas.arblist.matrikkel.download.convert.ConvertedGrenselinjeFile
import no.kartverket.nibas.arblist.matrikkel.download.convert.ConvertedGrensepunkt
import no.kartverket.nibas.arblist.matrikkel.download.convert.convertGrenselinje
import no.kartverket.nibas.arblist.matrikkel.download.convert.convertGrensepunkt
import java.io.File
import kotlin.io.extension


fun runConverter(endringsnummer: Long) {
    // ------------------------------------------------------------------------
    // Konverter til nibasvennlige flatbufferfiler
    // -------------------------------------------------------------------------
    val cwd = File(".").canonicalFile
    val downloadDir = File(cwd, "M22-DATA-$endringsnummer")
    val flatbufferFiles = downloadDir.listFiles().filter { it.extension == "fb" }
    val grenselinjeFiles = flatbufferFiles.filter { it.name.startsWith("grenselinje") }.sortedBy { it.name }
    val grensepunktFiles = flatbufferFiles.filter { it.name.startsWith("grensepunkt") }.sortedBy { it.name }
    val convertedGrensepunktPrefix = "matrikkel_grensepunkt"
    val convertedGrenselinjePrefix = "matrikkel_grenselinje"
    convertGrensepunkt(grensepunktFiles)
    convertGrenselinje(convertedGrenselinjePrefix, grenselinjeFiles, ConvertedGrensepunkt(cwd, convertedGrensepunktPrefix))

}

fun printData(grenselinjer: ConvertedGrenselinjeFile) {
    // ------------------------------------------------------------------------
    // Gå igjennom grenselinjene
    // -------------------------------------------------------------------------
//    grenselinjer.forEach { gl ->
//        val coords = gl.grense().coordinatesVector()
//        for (i in 0 until coords.length()) {
//            val coord = coords.get(i).run {
//                LocalCoord(x(), y())
//            }
//            println(coord)
//        }
//    }
    // ------------------------------------------------------------------------
    // Gå igjennom grenselinjene
    // -------------------------------------------------------------------------
    println(grenselinjer.size)

    var count = 0
    grenselinjer.forEach { grenselinje ->
        if (count < 10) {
            // adiministrativGrenseKode == 0 betyr at grenselinjen er en kommunegrense
            if (grenselinje.grense().administrativGrenseKode() != 0.toByte()) {
                println("=== Grenselinje Information ===")
                println("Grenselinje ID: ${grenselinje.id()}")

                // MatrikkelGrense properties
                val grense = grenselinje.grense()
                println("Administrativ Grensekode: ${grense.administrativGrenseKode()}")
                println("Hjelpelinje Kode: ${grense.hjelpelinjeKode()}")
                println("Omtvistet: ${grense.omtvistet()}")
                println("Terrengdetalj Kode: ${grense.terrengdetaljKode()}")
                println("Målemetode Kode: ${grense.maalemetodeKode()}")
                println("Målingsnøyaktighet: ${grense.maalingsnoyaktighet()}")


                println("Nøyaktighetsklasse: ${grense.noyaktighetsklasse()}")

                // Print kooridnater
                val coordinates = grense.coordinatesVector()
                if (coordinates != null) {
                    println("Antall koordinater: ${coordinates.length()}")
                    println("Print noen koordinater:")
                    val maxCoords = minOf(3, coordinates.length())
                    for (i in 0 until maxCoords) {
                        val coord = coordinates.get(i)
                        println(
                            "  Koordinat $i: x=${coord.x()}, y=${coord.y()} (skalert: x=${
                                coord.x().toDouble() / 100
                            }, y=${coord.y().toDouble() / 100})"
                        )
                    }
                } else {
                    println("No coordinates available")
                }
                println("==============================")
                count++
            }
        }

    }
}

fun main() {
//    val endringsnummer = 364590149L
////    Konverter til nibasvennlige flatbufferfiler
//    runConverter(endringsnummer)

    val cwd = File("Converted").canonicalFile
    val convertedGrenselinjePrefix = "matrikkel_grenselinje"
    val grenselinjer = ConvertedGrenselinjeFile(cwd, convertedGrenselinjePrefix)
    printData(grenselinjer)

}
