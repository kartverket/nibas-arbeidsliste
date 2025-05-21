package no.kartverket.nibas.nibasarbeidsliste.controller

import no.kartverket.nibas.nibasarbeidsliste.api.MatrikkelGrenselinjerApi
import no.kartverket.nibas.nibasarbeidsliste.dto.MatrikkelGrenselinjeFeatureCollection
import no.kartverket.nibas.nibasarbeidsliste.service.MatrikkelGrenselinjerService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Kontroller for å håndtere forespørsler om grenselinjer fra Matrikkelen.
 *
 * @property matrikkelGrenselinjerService Tjenesten som håndterer forretningslogikk for grenselinjer
 */
@RestController
@RequestMapping("/api/v1/matrikkel/grenselinjer")
class MatrikkelGrenselinjerController(
    private val matrikkelGrenselinjerService: MatrikkelGrenselinjerService
) : MatrikkelGrenselinjerApi {

    private val logger = LoggerFactory.getLogger(MatrikkelGrenselinjerController::class.java)

    /**
     * Henter grenselinjer en kommune fra Matrikkelen.
     * @param kommunenummer Kommunenumre som skal hentes grenselinjer for
     * @return FeatureCollection med grenselinjer i GeoJSON-format
     */
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE, "application/geo+json"])
    override fun hentGrenselinjer(
        @RequestParam(required = false) kommunenummer: String?
    ): ResponseEntity<MatrikkelGrenselinjeFeatureCollection> {
        logger.info("Mottatt forespørsel om grenselinjer for kommune: {}", kommunenummer ?: "ikke angitt")

        return try {
            if (kommunenummer.isNullOrBlank()) {
                logger.warn("Ingen gyldig kommunenummer ble angitt. Returnerer tomt resultat.")
                return ResponseEntity.ok(MatrikkelGrenselinjeFeatureCollection(features = emptyList()))
            }


            logger.debug("Henter grenselinjer for kommunenummer: {}", kommunenummer)

            val resultat = matrikkelGrenselinjerService.hentGrenselinjerForKommune(kommunenummer)

            if (resultat.features.isEmpty()) {
                logger.info("Ingen grenselinjer funnet for den angitte kommunen: {}", kommunenummer)
                ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            } else {
                ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Content-Type", "application/geo+json;charset=UTF-8")
                    .body(resultat)
            }
        } catch (e: IllegalArgumentException) {
            logger.error("Ugyldig forespørsel: {}", e.message, e)
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            logger.error("Uventet feil ved henting av grenselinjer: {}", e.message, e)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Henter alle tilgjengelige kommuner som har grenselinjer i systemet.
     *
     * @return Liste med kommunenumre som har tilgjengelige grenselinjer
     */
    @GetMapping("/kommuner", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun hentTilgjengeligeKommuner(): ResponseEntity<List<String>> {
        logger.debug("Henter alle tilgjengelige kommuner med grenselinjer")

        return try {
            val kommuner = matrikkelGrenselinjerService.hentTilgjengeligeKommuner()
            logger.info("Fant {} kommuner med tilgjengelige grenselinjer", kommuner.size)

            if (kommuner.isEmpty()) {
                ResponseEntity.noContent().build()
            } else {
                ResponseEntity.ok(kommuner.sorted())
            }
        } catch (e: Exception) {
            logger.error("Feil ved henting av tilgjengelige kommuner", e)
            ResponseEntity.internalServerError().build()
        }
    }
}
