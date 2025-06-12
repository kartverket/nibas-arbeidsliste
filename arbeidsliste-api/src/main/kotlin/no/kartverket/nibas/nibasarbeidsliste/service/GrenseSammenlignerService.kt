package no.kartverket.nibas.nibasarbeidsliste.service

import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import no.kartverket.nibas.nibasarbeidsliste.model.Kommune
import no.kartverket.nibas.nibasarbeidsliste.model.KoordinaterMedAvvik
import no.kartverket.nibas.nibasarbeidsliste.model.MatrikkelGrenselinje
import no.kartverket.nibas.nibasarbeidsliste.model.NibasGrense
import no.kartverket.nibas.nibasarbeidsliste.model.NibasKommune
import no.kartverket.nibas.nibasarbeidsliste.repository.AvvikRepository
import no.kartverket.nibas.nibasarbeidsliste.repository.MatrikkelGrenselinjeRepository
import no.kartverket.nibas.nibasarbeidsliste.util.LocalCoords
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt
import kotlin.time.measureTime

data class GrenseSammenligningResultat(
    val antallGrenserSjekket: Int,
    val antallGrenserMedAvvik: Int,
    val totaltAntallAvvik: Int,
    val toleranseMetreBrukt: Double,
    val antallAvvikLagret: Int,
    val tidsbrukMs: Long
)

data class SammenligningInternResultat(
    val antallGrenserMedAvvik: Int,
    val totaltAntallAvvik: Int,
    val antallAvvikLagret: Int
)

data class AvvikPunkt(
    val nibasKoordinat: Coordinate,
    val closestM22Koordinat: Coordinate?,
    val distanse: Double,
    val erPaaMatrikkelLinje: Boolean = false
)

data class GrenseResultat(
    val grenseId: String,
    val punkter: MutableList<Pair<Int, String>> = mutableListOf(),  // (point index, status) - keep for logging
    val avvikPunkter: MutableList<AvvikPunkt> = mutableListOf()     // detailed coordinate info
)

@Service
class GrenseSammenlignerService(
    private val matrikkelRepo: MatrikkelGrenselinjeRepository,
    private val nibasService: NibasGrenseService,
    private val avvikRepository: AvvikRepository
) {
    fun finnAvvik(toleranseMeter: Double): GrenseSammenligningResultat {
        // 1. Hent totalt antall NIBAS grenser først
        println("Henter totalt antall NIBAS grenser...")
        val initialResponse = nibasService.getGrenser(0, 1)
        val totaltAntallGrenser = initialResponse.totaltAntall
        println("Totalt $totaltAntallGrenser NIBAS grenser funnet")

        // 2. Hent alle NIBAS grenser
        // NIBAS grenser are stored in UTM33 in nibas database.
        println("\nHenter alle NIBAS grenser...")
        val nibasGrenser = nibasService.getGrenser(0, totaltAntallGrenser).innhold
        println("Fant ${nibasGrenser.size} NIBAS grenser")

        // 3. Hent M22 grenser
        // M22 grenser er lagert som LocalCoords i nibas database.
        println("\nHenter M22 grenser...")
        val m22Grenser = matrikkelRepo.findAll()
        println("Fant ${m22Grenser.size} M22 grenser")

        // 4. Sammenlign og lagre avvik
        var resultat: SammenligningInternResultat
        val totalTime = measureTime {
            resultat = sammenlignGrenser(nibasGrenser, m22Grenser, toleranseMeter)
        }


        return GrenseSammenligningResultat(
            antallGrenserSjekket = nibasGrenser.size,
            antallGrenserMedAvvik = resultat.antallGrenserMedAvvik,
            totaltAntallAvvik = resultat.totaltAntallAvvik,
            toleranseMetreBrukt = toleranseMeter,
            antallAvvikLagret = resultat.antallAvvikLagret,
            tidsbrukMs = totalTime.inWholeMilliseconds
        )
    }

    private fun sammenlignGrenser(
        nibasGrenser: List<NibasGrense>,
        m22Grenser: List<MatrikkelGrenselinje>,
        toleranseMeter: Double
    ): SammenligningInternResultat {
        // M22 points are stored as LocalCoords in database
        val allM22Points = m22Grenser.flatMap { m22 ->
            extractPoints(m22.geom as LineString)
        }
        println("Fant ${allM22Points.size} M22 punkter")

        // Build spatial index
        val spatialIndex = STRtree()
        val lineStringIndex = STRtree()
        val geometryFactory = GeometryFactory()

        // Spatial index stores LocalCoords
        allM22Points.forEach { m22Point ->
            val point = geometryFactory.createPoint(Coordinate(m22Point.x, m22Point.y))
            spatialIndex.insert(point.envelopeInternal, point)
        }
        spatialIndex.build()

        // Also index M22 LineStrings for fast proximity search (stored as LocalCoords)
        m22Grenser.forEach { m22 ->
            m22.geom?.let { lineString ->
                lineStringIndex.insert(lineString.envelopeInternal, lineString)
            }
        }
        lineStringIndex.build()
        println("Spatial index")
        println("\nProsesserer ${nibasGrenser.size} grenser...")

        val grenseResultater = mutableMapOf<String, GrenseResultat>()
        var totalAvvik = 0
        val falskePositiveToleranceLocal = toleranseMeter / LocalCoords.UTM33_RESOLUTION // Convert to LocalCoord units

        for ((grenseIndex, nibasGrense) in nibasGrenser.withIndex()) {
            // NIBAS points come in UTM33 coordinates (meters)
            val allNIBASPoints = extractPoints(nibasGrense.geometri.coordinates)

            // Progress logging every 10%
            val progress = (grenseIndex + 1) * 100 / nibasGrenser.size
            val prevProgress = grenseIndex * 100 / nibasGrenser.size
            if (progress / 10 > prevProgress / 10 || grenseIndex == nibasGrenser.size - 1) {
                println("Progress: $progress% - ${grenseIndex + 1}/${nibasGrenser.size} grenser done, $totalAvvik avvik found")
            }

            for ((index, nibasPoint) in allNIBASPoints.withIndex()) {
                // Convert NIBAS UTM33 to LocalCoords
                val nibasLocal = LocalCoords.fromUtm33(nibasPoint.x, nibasPoint.y)

                // SPEED DEMON: Use spatial index to find closest point!
                val searchRadius = toleranseMeter / LocalCoords.UTM33_RESOLUTION * 2 // Search wider area
                val nibasPointGeom = geometryFactory.createPoint(Coordinate(nibasLocal.x.toDouble(), nibasLocal.y.toDouble()))
                val envelope = nibasPointGeom.envelopeInternal
                envelope.expandBy(searchRadius)

                @Suppress("UNCHECKED_CAST")
                val nearbyPoints = spatialIndex.query(envelope) as List<Point>

                fun calculateDistance(m22Point: Coordinate): Double {
                    // m22Point is already LocalCoords from database
                    val m22Local = LocalCoords(m22Point.x.toInt(), m22Point.y.toInt())
                    val dx = (nibasLocal.x - m22Local.x).toDouble()
                    val dy = (nibasLocal.y - m22Local.y).toDouble()
                    // Convert LocalCoord distance back to meters
                    return sqrt(dx * dx + dy * dy) * LocalCoords.UTM33_RESOLUTION
                }

                val closestM22Coordinate = if (nearbyPoints.isNotEmpty()) {
                    nearbyPoints.minByOrNull { point -> calculateDistance(point.coordinate) }?.coordinate
                } else {
                    // Fallback: check all points if none found in radius
                    allM22Points.minByOrNull { m22Point -> calculateDistance(m22Point) }
                }

                val closestDistance = closestM22Coordinate?.let { calculateDistance(it) } ?: Double.MAX_VALUE

                // Comparison in meters
                if (closestDistance > toleranseMeter) {
                    // Check if NIBAS point lies on any nearby M22 LineString using spatial index
                    // Creating point with LocalCoord units
                    val nibasPointGeom = geometryFactory.createPoint(Coordinate(nibasLocal.x.toDouble(), nibasLocal.y.toDouble()))
                    // Buffer using LocalCoord units
                    val buffer = nibasPointGeom.buffer(falskePositiveToleranceLocal)

                    @Suppress("UNCHECKED_CAST")
                    // lineStringIndex contains LocalCoord LineStrings, querying with LocalCoord buffer
                    val nearbyLineStrings = lineStringIndex.query(buffer.envelopeInternal) as List<LineString>
                    val erPaaMatrikkelLinje = nearbyLineStrings.any { lineString ->
                        // Comparing LocalCoord LineString with LocalCoord point using LocalCoord tolerance
                        lineString.distance(nibasPointGeom) <= falskePositiveToleranceLocal
                    }

                    val grenseResultat = grenseResultater.getOrPut(nibasGrense.id) { GrenseResultat(nibasGrense.id) }
                    val avvikType = if (erPaaMatrikkelLinje) "Helper point" else "Real avvik"
                    grenseResultat.punkter.add(index to "$avvikType (${String.format("%.2f", closestDistance)} m)")

                    // Add detailed coordinate info with classification
                    grenseResultat.avvikPunkter.add(AvvikPunkt(
                        nibasKoordinat = nibasPoint,
                        closestM22Koordinat = closestM22Coordinate,
                        distanse = closestDistance,
                        erPaaMatrikkelLinje = erPaaMatrikkelLinje
                    ))
                    totalAvvik++
                }
            }
        }

        // Print summary with classification
        val antallGrenserMedAvvik = grenseResultater.count { it.value.punkter.isNotEmpty() }

        // Count real avvik vs helper points
        val allAvvikPunkter = grenseResultater.values.flatMap { it.avvikPunkter }
        val realAvvik = allAvvikPunkter.count { !it.erPaaMatrikkelLinje }
        val helperPoints = allAvvikPunkter.count { it.erPaaMatrikkelLinje }

        println("\n=== SAMMENDRAG ===")
        println("Totalt antall grenser: ${nibasGrenser.size}")
        println("Grenser med avvik: $antallGrenserMedAvvik")
        println("\nTotalt antall avvik: $totalAvvik")
        println("- Real avvik: $realAvvik")
        println("- Helper points på M22 linje: $helperPoints")
        println("\nBruker toleranse: $toleranseMeter meter")

//        // Write results to file
//        skrivResultaterTilFil(
//            nibasGrenser.size,
//            toleranseMeter,
//            antallGrenserMedAvvik,
//            totalAvvik,
//            realAvvik,
//            helperPoints,
//            grenseResultater
//        )

        // Convert to Avvik entities and save to database
        val avvikList = grenseResultater.values
            .filter { it.punkter.isNotEmpty() }
            .map { grenseResultat ->
                val nibasGrense = nibasGrenser.find { it.id == grenseResultat.grenseId }!!

                Avvik(
                    tolerance = toleranseMeter,
                    grenseId = nibasGrense.id,
                    lokalId = nibasGrense.lokalid,
                    grensetype = nibasGrense.grensetype,
                    geometri = convertToLineString(nibasGrense.geometri),
                    gyldigFra = nibasGrense.gyldighet.gyldigFra,
                    gyldigTil = nibasGrense.gyldighet.gyldigTil,
                    datafangstdato = nibasGrense.datafangstdato?.toString(),
                    foerstedigitaliseringsdato = nibasGrense.foerstedigitaliseringsdato.toString(),
                    opphav = nibasGrense.opphav,
                    informasjon = nibasGrense.informasjon,
                    endretAv = nibasGrense.endretAv,
                    endretDato = nibasGrense.endretDato.toString(),
                    typeEndring = nibasGrense.typeEndring,
                    maalemetode = nibasGrense.maalemetode,
                    noeyaktighet = nibasGrense.noeyaktighet,
                    kommuner = convertKommuner(nibasGrense.kommuner),
                    antallKoordinater = extractPoints(nibasGrense.geometri.coordinates).size,
                    antallKoordinaterMedAvvik = grenseResultat.avvikPunkter.size,
                    koordinaterMedAvvik = grenseResultat.avvikPunkter.map { avvikPunkt ->
                        val geometryFactory = GeometryFactory()
                        KoordinaterMedAvvik(
                            koordinatFraNibas = geometryFactory.createPoint(avvikPunkt.nibasKoordinat),
                            koordinatFraMatrikkelen = avvikPunkt.closestM22Koordinat?.let { m22LocalCoord ->
                                val m22Local = LocalCoords(m22LocalCoord.x.toInt(), m22LocalCoord.y.toInt())
                                val (utm33X, utm33Y) = m22Local.toUtm33()
                                geometryFactory.createPoint(Coordinate(utm33X, utm33Y))
                            },
                            distanseMellomKoordinater = avvikPunkt.distanse,
                            erPaaMatrikkelLinje = avvikPunkt.erPaaMatrikkelLinje
                        )
                    }
                )
            }

        val savedAvvik = avvikRepository.saveAll(avvikList)
        println("Lagret ${savedAvvik.size} avvik til database")

        return SammenligningInternResultat(
            antallGrenserMedAvvik = grenseResultater.count { it.value.punkter.isNotEmpty() },
            totaltAntallAvvik = totalAvvik,
            antallAvvikLagret = savedAvvik.size
        )
    }

    private fun extractPoints(lineString: LineString): List<Coordinate> {
        return (0 until lineString.numPoints).map { i ->
            lineString.coordinateSequence.getCoordinate(i)
        }
    }

    private fun extractPoints(coordinates: List<List<Double>>): List<Coordinate> {
        return coordinates.map { Coordinate(it[0], it[1]) }
    }

    private fun convertToLineString(nibasGeometri: no.kartverket.nibas.nibasarbeidsliste.model.NibasGeometri): LineString {
        val geometryFactory = GeometryFactory()
        val coordinates = nibasGeometri.coordinates.map { Coordinate(it[0], it[1]) }.toTypedArray()
        return geometryFactory.createLineString(coordinates)
    }

    private fun convertKommuner(nibasKommuner: List<NibasKommune>): List<Kommune> {
        return nibasKommuner.map { nibasKommune ->
            Kommune(
                fylkesLokalID = nibasKommune.fylkesLokalID,
                kommuneLokalID = nibasKommune.kommuneLokalID,
                kommunenummer = nibasKommune.kommunenummer,
                kommunenavn = nibasKommune.kommunenavn
            )
        }
    }

    private fun skrivResultaterTilFil(
        antallGrenser: Int,
        toleranseMeter: Double,
        antallGrenserMedAvvik: Int,
        totalAvvik: Int,
        realAvvik: Int,
        helperPoints: Int,
        grenseResultater: Map<String, GrenseResultat>
    ): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val filename = "avvik_results_${timestamp}.txt"
        val file = File(filename)

        file.writeText(buildString {
            appendLine("=== AVVIK SAMMENLIGNING RESULTAT ===")
            appendLine("Tidspunkt: ${LocalDateTime.now()}")
            appendLine("Antall grenser analysert: $antallGrenser")
            appendLine("Toleranse: $toleranseMeter meter")
            appendLine("Falsk positiv toleranse: $toleranseMeter meter")
            appendLine("Grenser med avvik: $antallGrenserMedAvvik")
            appendLine("Totalt antall avvik: $totalAvvik")
            appendLine("- Real avvik: $realAvvik")
            appendLine("- Helper points på M22 linje: $helperPoints")
            appendLine()
            appendLine("=== DETALJERT OVERSIKT ===")
            grenseResultater.values
                .filter { it.punkter.isNotEmpty() }
                .forEach { resultat ->
                    appendLine()
                    appendLine("Grense ${resultat.grenseId}:")
                    resultat.punkter.sortedBy { it.first }.forEach { (index, status) ->
                        appendLine("  - Punkt $index: $status")
                    }
                }
        })

        println("\nResultater skrevet til fil: $filename")
        return filename
    }
}
