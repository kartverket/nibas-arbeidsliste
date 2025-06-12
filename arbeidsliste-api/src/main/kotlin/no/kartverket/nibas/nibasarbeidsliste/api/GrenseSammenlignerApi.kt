package no.kartverket.nibas.nibasarbeidsliste.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.kartverket.nibas.nibasarbeidsliste.service.GrenseSammenligningResultat
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@RequestMapping("/api/grensekontroll")
@Tag(name = "Grensekontroll", description = "API for sammenligning av grenser mellom NIBAS og Matrikkelen")
interface GrenseSammenlignerApi {

    @Operation(
        summary = "Sammenlign alle grenser",
        description = "Sammenligner alle administrative grenser fra NIBAS med tilsvarende grenser i Matrikkelen og finner avvik basert på angitt toleranse"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Vellykket sammenligning utført",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GrenseSammenligningResultat::class))]
            ),
            ApiResponse(responseCode = "500", description = "Serverfeil under sammenligning", content = [Content()])
        ]
    )
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun sammenlignAlleGrenser(
        @RequestParam(defaultValue = "0.1") toleranseMeter: Double
    ): ResponseEntity<GrenseSammenligningResultat>
}