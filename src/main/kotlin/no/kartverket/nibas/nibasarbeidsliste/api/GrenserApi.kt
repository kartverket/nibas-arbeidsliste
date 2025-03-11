package no.kartverket.nibas.nibasarbeidsliste.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import reactor.core.publisher.Mono

@RequestMapping("/api/v1/grenser")
@Tag(name = "Grenser", description = "API for håndtering av grenser fra Nibas")
interface GrenserApi {

    @Operation(
        summary = "Hent grenser",
        description = "Henter grenser fra Nibas API med paginering"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Vellykket operasjon",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(responseCode = "500", description = "Serverfeil", content = [Content()])
        ]
    )
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentGrenser(
        @Parameter(description = "Sidenummer for paginering", example = "1")
        @RequestParam(defaultValue = "1") side: Int,
        @Parameter(description = "Antall elementer per side", example = "10")
        @RequestParam(defaultValue = "10") antall: Int
    ): Mono<String>

    @Operation(
        summary = "Hent grense med lokal ID",
        description = "Henter en spesifikk grense fra Nibas API basert på lokal ID"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Vellykket operasjon",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(responseCode = "404", description = "Grense ikke funnet", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Serverfeil", content = [Content()])
        ]
    )
    @GetMapping("/{lokalid}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentGrenseByLokalId(
        @Parameter(description = "Lokal ID for grensen", example = "123456")
        @PathVariable lokalid: String
    ): Mono<String>
}
