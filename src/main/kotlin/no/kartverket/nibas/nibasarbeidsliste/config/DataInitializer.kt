package no.kartverket.nibas.nibasarbeidsliste.config

import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus
import no.kartverket.nibas.nibasarbeidsliste.repository.AvvikRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * Komponent som initialiserer testdata for avvik ved oppstart av applikasjonen
 * Kjører kun i localhost-profilen for å unngå å generere testdata i produksjon
 */
@Component
@Profile("localhost")
class DataInitializer(
    private val avvikRepository: AvvikRepository
) {
    private val ANTALL_AVVIK = 20
    private val logger = LoggerFactory.getLogger(javaClass)
    private val random = Random(System.currentTimeMillis())

    // Oppretter geometryFactory med riktig SRID (25833) for EUREF89 UTM sone 33
    private val geometryFactory = GeometryFactory(PrecisionModel(), 25833)

    private val kommuner = listOf(
        "Oslo", "Bergen", "Trondheim", "Stavanger", "Drammen",
        "Fredrikstad", "Kristiansand", "Sandnes", "Tromsø", "Ålesund",
        "Tønsberg", "Moss", "Haugesund", "Sandefjord", "Arendal",
        "Bodø", "Larvik", "Hamar", "Halden", "Lillehammer"
    )

    private val grenseTyper = listOf(
        "KOMMUNEGRENSE", "FYLKESGRENSE", "RIKSGRENSE", "TERRITORIALGRENSE"
    )

    @EventListener(ApplicationReadyEvent::class)
    fun initData() {
        if (avvikRepository.count() > 0) {
            logger.info("Database har allerede {} avvik, hopper over initialisering", avvikRepository.count())
            return
        }
        logger.info("Starter initialisering av testdata for avvik...")
        try {
            val avvik = genererAvvik(ANTALL_AVVIK) //
            avvikRepository.saveAll(avvik)
            logger.info("Initialisert {} avvik i databasen", avvik.size)
        } catch (e: Exception) {
            logger.error("Feil ved initialisering av testdata: {}", e.message, e)
        }
    }

    private fun genererAvvik(antall: Int): List<Avvik> {
        val avvikListe = mutableListOf<Avvik>()

        for (i in 1..antall) {
            val kommune = kommuner.random(random)
            val grenseType = grenseTyper.random(random)

            val grense = genererTilfeldigGrense()

            val antallPunkter = random.nextInt(1, 4)
            val avvikPunkter = genererTilfeldigeAvvikPunkter(grense, antallPunkter)

            logger.info("Genererte {} avvikspunkter for avvik #{}", avvikPunkter.size, i)

            val status = AvvikStatus.NY

            val avvik = Avvik(
                kommuneNavn = kommune,
                grense = grense,
                avvikPunkter = avvikPunkter,
                status = status,
                grenseType = grenseType
            )

            avvikListe.add(avvik)
        }

        return avvikListe
    }

    /**
     * Genererer en tilfeldig grense (LineString) innenfor Norges fastland
     * Bruker EUREF89 UTM sone 33 (EPSG:25833) koordinatsystem
     */
    private fun genererTilfeldigGrense(): org.locationtech.jts.geom.LineString {
        // Disse koordinatene er godt innenfor Norges fastland
        val minX = 300000.00 // Vestlig grense
        val maxX = 400000.00 // Østlig grense
        val minY = 6600000.00 // Sørlig grense
        val maxY = 6800000.00 // Nordlig grense

        // Generer en tilfeldig startkoordinat innenfor bounding box med maks 2 desimaler
        val startX = Math.round(random.nextDouble(minX, maxX) * 100) / 100.0
        val startY = Math.round(random.nextDouble(minY, maxY) * 100) / 100.0

        // Generer 3-7 koordinater for grensen
        val antallKoordinater = random.nextInt(3, 8)
        val koordinater = mutableListOf<Coordinate>()

        var currentX = startX
        var currentY = startY

        for (i in 0 until antallKoordinater) {
            val roundedX = Math.round(currentX * 100) / 100.0
            val roundedY = Math.round(currentY * 100) / 100.0
            koordinater.add(Coordinate(roundedX, roundedY))

            currentX += Math.round(random.nextDouble(-1000.0, 1000.0) * 100) / 100.0
            currentY += Math.round(random.nextDouble(-1000.0, 1000.0) * 100) / 100.0

            currentX = currentX.coerceIn(minX, maxX)
            currentY = currentY.coerceIn(minY, maxY)
        }

        val lineString = geometryFactory.createLineString(koordinater.toTypedArray())
        lineString.setSRID(25833)
        return lineString
    }

    /**
     * Genererer tilfeldige avvikspunkter langs en grense
     * Bruker EUREF89 UTM sone 33 (EPSG:25833) koordinatsystem
     *
     * Et avvik er et punkt langs en grense hvor det er registrert en avvikelse fra den korrekte grensen.
     * Det kan være fra ett til alle punktene i grensen som har avvik.
     */
    private fun genererTilfeldigeAvvikPunkter(
        grense: org.locationtech.jts.geom.LineString,
        antall: Int
    ): List<org.locationtech.jts.geom.Point> {
        val punkter = mutableListOf<org.locationtech.jts.geom.Point>()
        val koordinater = grense.coordinates

        val faktiskAntall = minOf(antall, koordinater.size)

        val valgtePunkter = koordinater.indices.shuffled(random).take(faktiskAntall)

        for (index in valgtePunkter) {
            val koord = koordinater[index]

            val roundedX = Math.round(koord.x * 100) / 100.0
            val roundedY = Math.round(koord.y * 100) / 100.0
            val punkt = geometryFactory.createPoint(Coordinate(roundedX, roundedY))
            punkt.setSRID(25833)
            punkter.add(punkt)
        }

        logger.info("Genererte ${punkter.size} avvikspunkter")
        return punkter
    }
}
