package no.kartverket.nibas.nibasarbeidsliste.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus
import no.kartverket.nibas.nibasarbeidsliste.repository.AvvikRepository
import no.kartverket.nibas.nibasarbeidsliste.service.NibasGrenserService
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.PrecisionModel
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * Komponent som initialiserer testdata for avvik ved oppstart av applikasjonen
 * Kjører kun i localhost-profilen for å unngå å generere testdata i produksjon
 */
@Component
@Profile("localhost")
class DataInitializer(
    private val avvikRepository: AvvikRepository,
    private val nibasGrenserService: NibasGrenserService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()
    
    // EPSG:25833 is the coordinate system used in Norway
    private val geometryFactory = GeometryFactory(PrecisionModel(), 25833)

    // lokalID til grenser i NIBAS som har avvik
    private val lokalIDs = arrayOf(
        "7bcca8e4-718e-396d-8493-8f374c5d12fd",
        "98304de1-663e-327e-9c73-ed350156e67c",
        "edc00dab-bbbb-3987-876d-e5233dbcb08c",
        "6c3e890d-2915-3453-a7fb-4dd86c56f0de",
    )

    @EventListener(ApplicationReadyEvent::class)
    fun initData() {
        if (avvikRepository.count() > 0) {
            logger.info("Database har allerede {} avvik, hopper over initialisering", avvikRepository.count())
            return
        }

        logger.info("Starter initialisering av testdata for avvik fra Nibas API...")

        try {
            val avvik = hentAvvikFraNibas()
            avvikRepository.saveAll(avvik)
            logger.info("Initialisert {} avvik i databasen", avvik.size)
        } catch (e: Exception) {
            logger.error("Feil ved initialisering av testdata: {}", e.message, e)
        }
    }

    /**
     * Henter grenser fra Nibas API basert på lokalID-er og oppretter avvik for hver grense
     */
    private fun hentAvvikFraNibas(): List<Avvik> {
        val avvikListe = mutableListOf<Avvik>()

        for (lokalId in lokalIDs) {
            logger.info("Henter grense med lokalID={} fra Nibas API", lokalId)

            try {
                val grenseJson = nibasGrenserService.hentGrenseByLokalId(lokalId)
                    .doOnError { error ->
                        logger.error("Feil ved henting av grense med lokalID={}: {}", lokalId, error.message, error)
                    }
                    .blockOptional()
                    .orElse(null)

                if (grenseJson != null) {
                    logger.info("Opprettet avvik for grense med lokalID={}", lokalId)

                    // Parse JSON and extract fields
                    val avvik = parseGrenseJson(grenseJson)
                    avvikListe.add(avvik)
                } else {
                    logger.warn("Kunne ikke hente grense med lokalID={} fra Nibas API", lokalId)
                }
            } catch (e: Exception) {
                logger.error("Feil ved behandling av grense med lokalID={}: {}", lokalId, e.message, e)
            }
        }

        return avvikListe
    }

    /**
     * Parse grense JSON and create Avvik object with all fields
     */
    private fun parseGrenseJson(grenseJson: String): Avvik {
        try {
            val jsonNode = objectMapper.readTree(grenseJson)
            
            // Parse date fields
            val gyldigFra = parseLocalDate(jsonNode.path("gyldighet").path("gyldigFra").asText())
            val gyldigTil = if (jsonNode.path("gyldighet").path("gyldigTil").isNull) null 
                            else parseLocalDate(jsonNode.path("gyldighet").path("gyldigTil").asText())
            
            // Parse geometri to JTS LineString for PostGIS
            val lineString = parseGeometri(jsonNode.path("geometri"))
            
            // Create Avvik with all fields from JSON
            return Avvik(
                grenseId = jsonNode.path("id").asText(null),
                lokalId = jsonNode.path("lokalid").asText(null),
                grensetype = jsonNode.path("grensetype").asText(null),
                geometri = lineString,
                gyldigFra = gyldigFra,
                gyldigTil = gyldigTil,
                datafangstdato = jsonNode.path("datafangstdato").asText(null),
                foerstedigitaliseringsdato = jsonNode.path("foerstedigitaliseringsdato").asText(null),
                opphav = jsonNode.path("opphav").asText(null),
                informasjon = jsonNode.path("informasjon").asText(null),
                endretAv = jsonNode.path("endretAv").asText(null),
                endretDato = jsonNode.path("endretDato").asText(null),
                typeEndring = jsonNode.path("typeEndring").asText(null),
                maalemetode = jsonNode.path("maalemetode").asText(null),
                noeyaktighet = if (jsonNode.path("noeyaktighet").isInt) jsonNode.path("noeyaktighet").asInt() else null,
                registrertDato = LocalDateTime.now(),
                status = AvvikStatus.NY
            )
        } catch (e: Exception) {
            logger.error("Feil ved parsing av grense-JSON: {}", e.message, e)
            // Fallback to basic Avvik if parsing fails
            return Avvik(
                registrertDato = LocalDateTime.now(),
                status = AvvikStatus.NY
            )
        }
    }
    
    /**
     * Parse GeoJSON geometry to JTS LineString
     */
    private fun parseGeometri(geometriNode: JsonNode): LineString? {
        if (!geometriNode.isObject) return null
        
        try {
            val type = geometriNode.path("type").asText()
            if (type != "LineString") {
                logger.warn("Geometri er ikke av type LineString, men {}", type)
                return null
            }
            
            val coordinates = geometriNode.path("coordinates")
            if (!coordinates.isArray) return null
            
            val coords = mutableListOf<Coordinate>()
            
            for (i in 0 until coordinates.size()) {
                val point = coordinates.get(i)
                if (point.isArray && point.size() >= 2) {
                    val x = point.get(0).asDouble()
                    val y = point.get(1).asDouble()
                    coords.add(Coordinate(x, y))
                }
            }
            
            if (coords.isEmpty()) return null
            
            return geometryFactory.createLineString(coords.toTypedArray())
        } catch (e: Exception) {
            logger.error("Feil ved parsing av geometri: {}", e.message, e)
            return null
        }
    }
    
    /**
     * Parse date string to LocalDate
     */
    private fun parseLocalDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        
        return try {
            LocalDate.parse(dateStr)
        } catch (e: DateTimeParseException) {
            logger.warn("Kunne ikke parse dato: {}", dateStr)
            null
        }
    }
}
