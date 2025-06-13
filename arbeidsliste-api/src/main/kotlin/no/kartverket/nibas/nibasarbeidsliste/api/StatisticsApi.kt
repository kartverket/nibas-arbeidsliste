package no.kartverket.nibas.nibasarbeidsliste.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikStatisticsDTO
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping("/api/v1/statistikk")
@Tag(name = "Statistikk", description = "API for statistikk over avvik mellom grenser")
interface StatisticsApi {

    @Operation(
        summary = "Hent avvik statistikk",
        description = "Henter oversikt over avvik i systemet med totale tall, status-fordeling og grensetype-statistikk"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Vellykket operasjon",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = AvvikStatisticsDTO::class))]
            ),
            ApiResponse(responseCode = "500", description = "Serverfeil", content = [Content()])
        ]
    )
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentAvvikStatistikk(): ResponseEntity<AvvikStatisticsDTO>
}
