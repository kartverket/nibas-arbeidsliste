package no.kartverket.nibas.nibasarbeidsliste.service

import no.kartverket.nibas.nibasarbeidsliste.dto.MatrikkelGrenselinjeDto
import no.kartverket.nibas.nibasarbeidsliste.dto.MatrikkelGrenselinjeFeatureCollection
import no.kartverket.nibas.nibasarbeidsliste.repository.MatrikkelGrenselinjeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for handling operations related to MatrikkelGrenselinje entities.
 * Converts database entities to GeoJSON format with proper coordinate scaling.
 */
@Service
@Transactional(readOnly = true)
class MatrikkelGrenselinjerService(
    private val repository: MatrikkelGrenselinjeRepository,
) {
    private val logger = LoggerFactory.getLogger(MatrikkelGrenselinjerService::class.java)

    /**
     * Retrieves boundary lines for a single municipality.
     * @param kommunenummer Municipality number to filter by (exact match)
     * @return FeatureCollection containing the boundary lines in GeoJSON format
     */
    fun hentGrenselinjerForKommune(kommunenummer: String): MatrikkelGrenselinjeFeatureCollection {
        logger.debug("Henter grenselinjer for kommunenummer: {}", kommunenummer)

        val entities = repository.findByKommune(kommunenummer)

        val features = entities.map { MatrikkelGrenselinjeDto.fromEntity(it) }
            .distinctBy { it.properties.id }

        return MatrikkelGrenselinjeFeatureCollection(
            type = "FeatureCollection",
            features = features
        )
    }

    /**
     * Retrieves all unique municipality numbers that have boundary lines in the database.
     * @return List of unique municipality numbers, sorted numerically
     */
    @Transactional(readOnly = true)
    fun hentTilgjengeligeKommuner(): List<String> {
        logger.debug("Henter alle tilgjengelige kommuner med grenselinjer")
        return repository.findDistinctKommuner()
    }
}
