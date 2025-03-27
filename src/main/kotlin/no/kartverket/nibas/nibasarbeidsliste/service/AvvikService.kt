package no.kartverket.nibas.nibasarbeidsliste.service

import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.GeoJsonLineString
import no.kartverket.nibas.nibasarbeidsliste.dto.GeoJsonPoint
import no.kartverket.nibas.nibasarbeidsliste.dto.KommuneDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.KoordinaterMedAvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import no.kartverket.nibas.nibasarbeidsliste.repository.AvvikRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class AvvikService(
    private val avvikRepository: AvvikRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun hentAlleAvvik(pageable: Pageable): Page<AvvikDTO> {
        logger.info("Henter avvik for side {} med antall {}", pageable.pageNumber, pageable.pageSize)
        return avvikRepository.findAll(pageable).map { convertToDTO(it) }
    }

    private fun convertToDTO(avvik: Avvik): AvvikDTO {
        val geoJsonGeometri = if (avvik.geometri != null) {
            val coordinates = mutableListOf<List<Double>>()
            for (i in 0 until avvik.geometri.numPoints) {
                val point = avvik.geometri.getPointN(i)
                coordinates.add(listOf(point.x, point.y))
            }
            GeoJsonLineString(coordinates = coordinates)
        } else null

        return AvvikDTO(
            id = avvik.id,
            registrertDato = avvik.registrertDato,
            status = avvik.status,
            harGeometri = avvik.geometri != null,
            grenseId = avvik.grenseId,
            lokalId = avvik.lokalId,
            grensetype = avvik.grensetype,
            geometri = geoJsonGeometri,
            gyldigFra = avvik.gyldigFra,
            gyldigTil = avvik.gyldigTil,
            datafangstdato = avvik.datafangstdato,
            foerstedigitaliseringsdato = avvik.foerstedigitaliseringsdato,
            opphav = avvik.opphav,
            informasjon = avvik.informasjon,
            endretAv = avvik.endretAv,
            endretDato = avvik.endretDato,
            typeEndring = avvik.typeEndring,
            maalemetode = avvik.maalemetode,
            noeyaktighet = avvik.noeyaktighet,
            antallKoordinater = avvik.antallKoordinater,
            antallKoordinaterMedAvvik = avvik.antallKoordinaterMedAvvik,
            tolerance = avvik.tolerance,
            koordinaterMedAvvik = avvik.koordinaterMedAvvik?.map { koordinat ->
                KoordinaterMedAvvikDTO(
                    nibasKoordinat = GeoJsonPoint(coordinates = listOf(koordinat.koordinatFraNibas?.x ?: 0.0, koordinat.koordinatFraNibas?.y ?: 0.0)),
                    matrikkelKoordinat = GeoJsonPoint(coordinates = listOf(koordinat.koordinatFraMatrikkelen?.x ?: 0.0, koordinat.koordinatFraMatrikkelen?.y
                        ?: 0.0)),
                    distanseMellomKoordinater = koordinat.distanseMellomKoordinater
                )
            },
            kommuner = avvik.kommuner?.map { kommune ->
                KommuneDTO(
                    fylkesLokalID = kommune.fylkesLokalID,
                    kommuneLokalID = kommune.kommuneLokalID,
                    kommunenummer = kommune.kommunenummer,
                    kommunenavn = kommune.kommunenavn
                )
            },
        )
    }
}
