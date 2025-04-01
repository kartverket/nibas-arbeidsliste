package no.kartverket.nibas.nibasarbeidsliste.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.BulkAvvikRequestDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.KommuneAvvikDTO
import org.springframework.data.domain.Page
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@RequestMapping("/api/v1/avvik")
@Tag(name = "Avvik", description = "API for håndtering av avvik mellom grenser")
interface AvvikApi {

    @Operation(
        summary = "Hent alle avvik",
        description = "Henter alle registrerte avvik mellom administrative grenser i NIBAS og Matrikkelen"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Vellykket operasjon",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = Page::class))]
            ),
            ApiResponse(responseCode = "500", description = "Serverfeil", content = [Content()])
        ]
    )
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentAlleAvvik(
        @RequestParam(required = false) grensetyper: List<String>?,
        @RequestParam(defaultValue = "0") side: Int,
        @RequestParam(defaultValue = "10") antall: Int
    ): ResponseEntity<Page<AvvikDTO>>


    @Operation(
        summary = "Alle avvik for en gitt kommune",
        description = "Henter alle registrerte avvik mellom administrative grenser i NIBAS og Matrikkelen for en gitt kommune"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Vellykket operasjon",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = Page::class))]
            ),
            ApiResponse(responseCode = "500", description = "Serverfeil", content = [Content()])
        ]
    )
    @GetMapping(path = ["/kommune/{lokalId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentAvvik(
        @PathVariable lokalId: String,
        @RequestParam(required = false) grensetyper: List<String>?
    ): ResponseEntity<List<AvvikDTO>>


    @Operation(
        summary = "Liste med kommuner med avvik",
        description = "Henter liste over kommuner med avvik og antall avvik per kommune"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Vellykket operasjon",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = KommuneAvvikDTO::class))]
            ),
            ApiResponse(responseCode = "500", description = "Serverfeil", content = [Content()])
        ]
    )
    @GetMapping(path = ["/kommuner"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentKommunerMedAvvikSummary(
        @RequestParam(required = false) grensetyper: List<String>?,
        @RequestParam(defaultValue = "0") side: Int,
        @RequestParam(defaultValue = "10") antall: Int
    ): ResponseEntity<Page<KommuneAvvikDTO>>

    @Operation(
        summary = "Oppdater flere avvik",
        description = "Oppdaterer flere avvik samtidig med ny status og informasjon"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Vellykket operasjon",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = List::class))]
            ),
            ApiResponse(responseCode = "400", description = "Ugyldig forespørsel", content = [Content()]),
            ApiResponse(responseCode = "404", description = "Avvik ikke funnet", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Serverfeil", content = [Content()])
        ]
    )
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun oppdaterFlereAvvik(@RequestBody updates: BulkAvvikRequestDTO): ResponseEntity<List<AvvikDTO>>
}
