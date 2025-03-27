package no.kartverket.nibas.nibasarbeidsliste.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus
import no.kartverket.nibas.nibasarbeidsliste.model.Kommune
import no.kartverket.nibas.nibasarbeidsliste.model.KoordinaterMedAvvik
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
import java.io.InputStream

/**
 * Komponent som initialiserer data med avvik ved oppstart av applikasjonen
 * Kjører kun i localhost-profilen.
 */
@Component
@Profile("localhost")
class DataInitializer(
    private val avvikRepository: AvvikRepository,
    private val nibasGrenserService: NibasGrenserService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    private val geometryFactory = GeometryFactory(PrecisionModel(), 25833)

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
        // Henter avvik fra JSON-fil
        val mockData = readMockData()

        for ((lokalId, _) in mockData) {
            logger.info("Henter grense med lokalID={} fra Nibas API", lokalId)

            val response = try {
                // Henter grense fra Nibas API
                nibasGrenserService.hentGrenseByLokalId(lokalId)
                    .doOnError { error ->
                        logger.error("Feil ved henting av grense med lokalID={}: {}", lokalId, error.message, error)
                    }
                    .blockOptional()
                    .orElse(null)
            } catch (e: Exception) {
                logger.error("Feil ved henting av grense med lokalID={} fra Nibas API", lokalId, e)
                null
            }

            if (response != null) {
                logger.info("Opprettet avvik for grense med lokalID={}", lokalId)

                val grense = parseGrenseJson(response)
                val mockDataForGrense = mockData[lokalId]
                val avvik = createAvvik(grense, mockDataForGrense)
                avvikListe.add(avvik)
            } else {
                logger.warn("Kunne ikke hente grense med lokalID={} fra Nibas API", lokalId)
            }
        }

        return avvikListe
    }


    private fun parseGrenseJson(grenseJson: String): Grense {
        try {
            val jsonNode = objectMapper.readTree(grenseJson)

            val gyldigFra = parseLocalDate(jsonNode.path("gyldighet").path("gyldigFra").asText())
            val gyldigTil = if (jsonNode.path("gyldighet").path("gyldigTil").isNull) null
            else parseLocalDate(jsonNode.path("gyldighet").path("gyldigTil").asText())

            val lineString = parseGeometri(jsonNode.path("geometri"))
            val kommuner = parseKommuner(jsonNode.path("kommuner"))

            return Grense(
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
                kommuner = kommuner
            )
        } catch (e: Exception) {
            logger.error("Feil ved parsing av grense-JSON: {}", e.message, e)
            return Grense()
        }
    }

    private fun parseKommuner(kommunerNode: JsonNode): List<KommuneData>? {
        if (!kommunerNode.isArray) return null

        val kommuneListe = mutableListOf<KommuneData>()

        for (i in 0 until kommunerNode.size()) {
            val kommuneNode = kommunerNode.get(i)
            kommuneListe.add(
                KommuneData(
                    fylkesLokalID = kommuneNode.path("fylkesLokalID").asText(null),
                    kommuneLokalID = kommuneNode.path("kommuneLokalID").asText(null),
                    kommunenummer = kommuneNode.path("kommunenummer").asText(null),
                    kommunenavn = kommuneNode.path("kommunenavn").asText(null)
                )
            )
        }

        return if (kommuneListe.isEmpty()) null else kommuneListe
    }

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

    private fun parseLocalDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null

        return try {
            LocalDate.parse(dateStr)
        } catch (e: DateTimeParseException) {
            logger.warn("Kunne ikke parse dato: {}", dateStr)
            null
        }
    }

    private fun readMockData(): Map<String, AvvikJson> {
        logger.info("Leser data fra json fil...")
        try {
            val fileName = "borders_with_avvik.json"
            val resourceStream: InputStream = javaClass.classLoader.getResourceAsStream(fileName)
                ?: throw IllegalStateException("Kunne ikke finne $fileName i resources folderen")

            val mapper = ObjectMapper().registerKotlinModule()
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

            logger.debug("JSON content: {}", resourceStream.bufferedReader().use { it.readText() })

            val resourceStreamForReading = javaClass.classLoader.getResourceAsStream(fileName)
                ?: throw IllegalStateException("Kunne ikke finne $fileName i resources folderen")

            val avvikList: List<AvvikJson> = mapper.readValue(resourceStreamForReading)

            return avvikList.associateBy { it.lokalid }
        } catch (e: Exception) {
            logger.error("Error reading mock data: {}", e.message, e)
            throw e
        }
    }

    private fun createAvvik(grense: Grense, mockData: AvvikJson?): Avvik {
        return Avvik(
            // Fra Nibas API
            grenseId = grense.grenseId,
            lokalId = grense.lokalId,
            grensetype = grense.grensetype,
            geometri = grense.geometri,
            gyldigFra = grense.gyldigFra,
            gyldigTil = grense.gyldigTil,
            datafangstdato = grense.datafangstdato,
            foerstedigitaliseringsdato = grense.foerstedigitaliseringsdato,
            opphav = grense.opphav,
            informasjon = grense.informasjon,
            endretAv = grense.endretAv,
            endretDato = grense.endretDato,
            typeEndring = grense.typeEndring,
            maalemetode = grense.maalemetode,
            noeyaktighet = grense.noeyaktighet,
            kommuner = grense.kommuner?.map { kommuneData ->
                Kommune(
                    fylkesLokalID = kommuneData.fylkesLokalID,
                    kommuneLokalID = kommuneData.kommuneLokalID,
                    kommunenummer = kommuneData.kommunenummer,
                    kommunenavn = kommuneData.kommunenavn
                )
            },

            // Fra mock data
            antallKoordinater = mockData?.totalCoordinates,
            antallKoordinaterMedAvvik = mockData?.mismatches,
            koordinaterMedAvvik = mockData?.mismatchedCoordinates?.map { coord ->
                KoordinaterMedAvvik(
                    koordinatFraNibas = geometryFactory.createPoint(Coordinate(coord.nibasX, coord.nibasY)),
                    koordinatFraMatrikkelen = geometryFactory.createPoint(Coordinate(coord.matrikkelX, coord.matrikkelY)),
                    distanseMellomKoordinater = coord.distanceMeters
                )
            },
            tolerance = mockData?.tolerance,
            registrertDato = LocalDateTime.now(),
            status = AvvikStatus.NY
        )
    }

    // Grense fra nibas
    data class Grense(
        val grenseId: String? = null,
        val lokalId: String? = null,
        val grensetype: String? = null,
        val geometri: LineString? = null,
        val gyldigFra: LocalDate? = null,
        val gyldigTil: LocalDate? = null,
        val datafangstdato: String? = null,
        val foerstedigitaliseringsdato: String? = null,
        val opphav: String? = null,
        val informasjon: String? = null,
        val endretAv: String? = null,
        val endretDato: String? = null,
        val typeEndring: String? = null,
        val maalemetode: String? = null,
        val noeyaktighet: Int? = null,
        val kommuner: List<KommuneData>? = null
    )

    data class KommuneData(
        val fylkesLokalID: String? = null,
        val kommuneLokalID: String? = null,
        val kommunenummer: String? = null,
        val kommunenavn: String? = null
    )

    data class AvvikJson(
        val id: Long = 0,
        val lokalid: String,
        val grensetype: String,
        val mismatches: Int,
        val totalCoordinates: Int,
        val mismatchPercentage: Double,
        val tolerance: Int,
        val mismatchedCoordinates: List<MismatchedCoordinate>
    )

    data class MismatchedCoordinate(
        val nibasX: Double,
        val nibasY: Double,
        val matrikkelX: Double,
        val matrikkelY: Double,
        val distanceMeters: Double
    )
}
