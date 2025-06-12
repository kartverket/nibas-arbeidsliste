package no.kartverket.nibas.nibasarbeidsliste.service

import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikRequestDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.GeoJsonLineString
import no.kartverket.nibas.nibasarbeidsliste.dto.GeoJsonPoint
import no.kartverket.nibas.nibasarbeidsliste.dto.KommuneAvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.KommuneDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.KoordinaterMedAvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus
import no.kartverket.nibas.nibasarbeidsliste.repository.AvvikRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AvvikService(
    private val avvikRepository: AvvikRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Henter en paginert liste med alle registrerte avvik.
     *
     * @param grensetyper Liste med grensetyper som skal inkluderes i søket
     * @param pageable Paginering og sortering
     * @return En [Page] med [AvvikDTO].
     */
    fun hentAlleAvvik(grensetyper: List<String>?, pageable: Pageable): Page<AvvikDTO> {
        logger.info("Henter alle avvik med grensetyper: {} og paginering: {}", grensetyper, pageable)
        val avvik = if (!grensetyper.isNullOrEmpty()) {
            avvikRepository.findAllByGrensetyper(grensetyper, pageable)
        } else {
            avvikRepository.findAll(pageable)
        }
        val avvikDto = avvik.mapNotNull { avvik ->
            val dto = convertToDTO(avvik)
            // Only return avvik that have real avvik points (not just helper points)
            if (dto.antallKoordinaterMedAvvik != null && dto.antallKoordinaterMedAvvik > 0) dto else null
        }
        return PageImpl(avvikDto, pageable, avvikDto.size.toLong())
    }

    /**
     * Henter alle avvik knyttet til en spesifikk kommune via lokalId.
     *
     * @param lokalid Den unike lokalId (UUID) for kommunen slik den er registrert i avvikets kommuneliste.
     * @param grensetyper Liste med grensetyper som skal inkluderes i søket
     * @return En liste ([List]) med [AvvikDTO] for den gitte kommunens lokalId. Returnerer en tom liste hvis ingen avvik finnes.
     */
    fun hentAvvik(lokalid: String, grensetyper: List<String>?): List<AvvikDTO> {
        logger.info("Henter alle avvik for kommune med lokalId {} med grensetyper {}", lokalid, grensetyper)
        val avvikList = if (!grensetyper.isNullOrEmpty()) {
            avvikRepository.findKommuneByLokalIdAndGrensetyper(lokalid, grensetyper)
        } else {
            avvikRepository.findKommuneByLokalId(lokalid)
        }
        return avvikList.mapNotNull { avvik ->
            val dto = convertToDTO(avvik)
            // Only return avvik that have real avvik points (not just helper points)
            if (dto.antallKoordinaterMedAvvik != null && dto.antallKoordinaterMedAvvik > 0) dto else null
        }
    }

    /**
     * Henter en paginert list av kommuner med avvik, sortert etter antall avvik (synkende).
     *
     * @param pageable Pagineringinformasjon (sidenummer, antall per side).
     * @return En [Page] med [KommuneAvvikDTO].
     */
    fun hentKommunerMedAvvikSummary(grensetyper: List<String>?, pageable: Pageable): Page<KommuneAvvikDTO> {
        logger.info("Henter paginert oppsummering av kommuner med avvik. Filtre: grensetyper={}, Side: {}, Antall: {}", grensetyper, pageable.pageNumber, pageable.pageSize)

        val allowedStatuses = setOf(AvvikStatus.NY, AvvikStatus.UNDER_BEHANDLING, AvvikStatus.VENT)

        return avvikRepository.findKommuneAvvikSummaryPage(
            statuses = allowedStatuses,
            grensetyper = grensetyper,
            pageable = pageable
        )
    }

    /**
     * Oppdaterer flere avvik samtidig.
     *
     * @param updates Liste med oppdateringer for avvik
     * @return Liste med oppdaterte [AvvikDTO]
     * @throws IllegalArgumentException hvis noen av ids ikke finnes
     */
    fun oppdaterAvvik(updates: List<AvvikRequestDTO>): List<AvvikDTO> {
        logger.info("Oppdaterer {} avvik", updates.size)

        val ids = updates.map { it.id }
        val existingAvvik = avvikRepository.findAllByIds(ids)

        if (existingAvvik.size != updates.size) {
            val missingIds = ids - existingAvvik.map { it.id }.toSet()
            throw IllegalArgumentException("Fant ikke avvik med ids: $missingIds")
        }

        val updatedAvvik = updates.map { update ->
            val avvik = existingAvvik.find { it.id == update.id }
                ?: throw IllegalArgumentException("Fant ikke avvik med id: ${update.id}")

            avvik.copy(
                status = update.status,
                endretDato = LocalDateTime.now().toString(),
            )
        }

        val savedAvvik = avvikRepository.saveAll(updatedAvvik)
        return savedAvvik.map { convertToDTO(it) }
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

        // Filter out helper points - only keep real avvik
        val realAvvikKoordinater = avvik.koordinaterMedAvvik?.filter { 
            it.erPaaMatrikkelLinje != true 
        }

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
            antallKoordinaterMedAvvik = realAvvikKoordinater?.size,
            tolerance = avvik.tolerance,
            koordinaterMedAvvik = realAvvikKoordinater?.map { koordinat ->
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
                    kommuneNummer = kommune.kommunenummer,
                    kommuneNavn = kommune.kommunenavn
                )
            },
        )
    }
}
