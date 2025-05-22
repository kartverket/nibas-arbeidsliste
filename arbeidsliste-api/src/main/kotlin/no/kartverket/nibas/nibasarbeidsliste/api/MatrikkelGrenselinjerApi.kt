package no.kartverket.nibas.nibasarbeidsliste.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.kartverket.nibas.nibasarbeidsliste.dto.MatrikkelGrenselinjeFeatureCollection
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * API for å hente grenselinjer fra Matrikkelen
 */
@Tag(
    name = "Matrikkel Grenselinjer",
    description = "API for å hente grenselinjer fra Matrikkelen"
)
@RequestMapping("/api/v1/matrikkel/grenselinjer")
interface MatrikkelGrenselinjerApi {

    /**
     * Henter grenselinjer for en kommune fra Matrikkelen
     * @param kommunenummer Kommunenummer
     * @return FeatureCollection med grenselinjer i GeoJSON-format
     */
    @Operation(
        summary = "Hent grenselinjer for en kommune",
        description = "Henter alle grenselinjer for en kommuner fra Matrikkelen. "
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Grenselinjene ble funnet",
                content = [
                    Content(
                        mediaType = "application/geo+json",
                        schema = Schema(implementation = MatrikkelGrenselinjeFeatureCollection::class)
                    )
                ]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Ugyldig forespørsel",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Ingen grenselinjer funnet for kommunen",
                content = [Content()]
            )
        ]
    )
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE, "application/geo+json"])
    fun hentGrenselinjer(
        @RequestParam(required = false) kommunenummer: String?
    ): ResponseEntity<MatrikkelGrenselinjeFeatureCollection>

    /**
     * Henter alle tilgjengelige kommuner som har grenselinjer i systemet
     *
     * @return Liste med kommunenumre som har tilgjengelige grenselinjer
     */
    @Operation(
        summary = "Hent tilgjengelige kommuner",
        description = "Henter en liste over alle kommuner som har tilgjengelige grenselinjer"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Liste over tilgjengelige kommuner",
                useReturnTypeSchema = true
            )
        ]
    )
    @GetMapping("/kommuner", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentTilgjengeligeKommuner(): ResponseEntity<List<String>>
}
