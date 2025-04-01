package no.kartverket.nibas.nibasarbeidsliste.service

import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.GeoJsonLineString
import no.kartverket.nibas.nibasarbeidsliste.dto.GeoJsonPoint
import no.kartverket.nibas.nibasarbeidsliste.dto.KommuneAvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.KommuneDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.KoordinaterMedAvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import no.kartverket.nibas.nibasarbeidsliste.repository.AvvikRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class AvvikService(
    private val avvikRepository: AvvikRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Henter en paginert liste over alle registrerte avvik.
     *
     * @param pageable Pagineringinformasjon (sidenummer, antall per side).
     * @return En [Page] med [AvvikDTO].
     */
    fun hentAlleAvvik(pageable: Pageable): Page<AvvikDTO> {
        logger.info("Henter avvik for side {} med antall {}", pageable.pageNumber, pageable.pageSize)
        return avvikRepository.findAll(pageable).map { convertToDTO(it) }
    }


    /**
     * Henter alle avvik knyttet til en spesifikk kommune via lokalId.
     *
     * @param lokalid Den unike lokalId (UUID) for kommunen slik den er registrert i avvikets kommuneliste.
     * @return En liste ([List]) med [AvvikDTO] for den gitte kommunens lokalId. Returnerer en tom liste hvis ingen avvik finnes.
     */
    fun hentAvvik(lokalid: String): List<AvvikDTO> {
        logger.info("Henter alle avvik for kommune med lokalId {}", lokalid)
        val avvikList = avvikRepository.findKommuneByLokalId(lokalid)
        return avvikList.map { convertToDTO(it) }
    }

    /**
     * Henter en paginert list av kommuner med avvik, sortert etter antall avvik (synkende).
     *
     * @param pageable Pagineringinformasjon (sidenummer, antall per side).
     * @return En [Page] med [KommuneAvvikDTO].
     */
    fun hentKommunerMedAvvikSummary(pageable: Pageable): Page<KommuneAvvikDTO> {
        logger.info("Henter paginert oppsummering av kommuner med avvik. Side: {}, Antall: {}", pageable.pageNumber, pageable.pageSize)
        val alleAvvik = avvikRepository.findAll()

        // Map til å holde oversikt over antall avvik per kommune
        val kommuneAvvikMap = mutableMapOf<String, KommuneAvvikDTO>()

        // Teller avvik per kommune
        alleAvvik.forEach { avvik ->
            avvik.kommuner?.forEach { kommune ->
                if (kommune.kommunenavn != null && kommune.kommunenummer != null) {
                    val key = "${kommune.kommunenummer}:${kommune.kommunenavn}"
                    val existing = kommuneAvvikMap[key]
                    if (existing == null) {
                        kommuneAvvikMap[key] = KommuneAvvikDTO(
                            kommunenavn = kommune.kommunenavn,
                            kommunenummer = kommune.kommunenummer,
                            kommunelokalid = kommune.kommuneLokalID,
                            fylkeslokalid = kommune.fylkesLokalID,
                            antallAvvik = 1,
                        )
                    } else {
                        kommuneAvvikMap[key] = existing.copy(antallAvvik = existing.antallAvvik + 1)
                    }
                }
            }
        }

        // Henter sortert liste med mest avvik først
        val sortedSummaryList = kommuneAvvikMap.values.sortedByDescending { it.antallAvvik }

        // Implementerer manuell paginering på den sorterte listen
        val start = pageable.offset.toInt()
        val end = (start + pageable.pageSize).coerceAtMost(sortedSummaryList.size)

        val pageContent = if (start <= end) {
            sortedSummaryList.subList(start, end)
        } else {
            emptyList()
        }

        return PageImpl(pageContent, pageable, sortedSummaryList.size.toLong())
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
