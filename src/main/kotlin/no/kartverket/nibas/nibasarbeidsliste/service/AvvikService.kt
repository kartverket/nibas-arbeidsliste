package no.kartverket.nibas.nibasarbeidsliste.service

import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.GeoJsonLineString
import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import no.kartverket.nibas.nibasarbeidsliste.repository.AvvikRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AvvikService(
    private val avvikRepository: AvvikRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun hentAlleAvvik(): List<AvvikDTO> {
        logger.info("Henter alle avvik")
        return avvikRepository.findAll().map { it.tilDTO() }
    }

    private fun Avvik.tilDTO(): AvvikDTO {
        // Convert JTS LineString to GeoJsonLineString
        val geoJsonGeometri = if (geometri != null) {
            val coordinates = mutableListOf<List<Double>>()
            for (i in 0 until geometri.numPoints) {
                val point = geometri.getPointN(i)
                coordinates.add(listOf(point.x, point.y))
            }
            GeoJsonLineString(coordinates = coordinates)
        } else null

        return AvvikDTO(
            id = id,
            registrertDato = registrertDato,
            status = status,
            harGeometri = geometri != null,
            grenseId = grenseId,
            lokalId = lokalId,
            grensetype = grensetype,
            geometri = geoJsonGeometri,
            gyldigFra = gyldigFra,
            gyldigTil = gyldigTil,
            datafangstdato = datafangstdato,
            foerstedigitaliseringsdato = foerstedigitaliseringsdato,
            opphav = opphav,
            informasjon = informasjon,
            endretAv = endretAv,
            endretDato = endretDato,
            typeEndring = typeEndring,
            maalemetode = maalemetode,
            noeyaktighet = noeyaktighet
        )
    }
}
