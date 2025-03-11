package no.kartverket.nibas.nibasarbeidsliste.controller

import no.kartverket.nibas.nibasarbeidsliste.api.AvvikApi
import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.service.AvvikService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/avvik")
class AvvikController(private val avvikService: AvvikService) : AvvikApi {
    private val logger = LoggerFactory.getLogger(AvvikController::class.java)

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun hentAlleAvvik(): ResponseEntity<List<AvvikDTO>> {
        logger.info("Henter alle avvik")
        val avvik = avvikService.hentAlleAvvik()
        return ResponseEntity.ok(avvik)
    }
}
