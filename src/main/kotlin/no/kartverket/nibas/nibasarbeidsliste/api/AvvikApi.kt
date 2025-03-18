package no.kartverket.nibas.nibasarbeidsliste.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikDTO
import org.springframework.data.domain.Page
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
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
        @RequestParam(defaultValue = "0") side: Int,
        @RequestParam(defaultValue = "10") antall: Int
    ): ResponseEntity<Page<AvvikDTO>>
}
