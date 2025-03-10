package no.kartverket.nibas.nibasarbeidsliste.controller

import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.service.AvvikService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/avvik")
class AvvikController(private val avvikService: AvvikService) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun hentAlleAvvik(): ResponseEntity<List<AvvikDTO>> {
        logger.info("REST-kall: Henter alle avvik")
        val avvik = avvikService.hentAlleAvvik()
        return ResponseEntity.ok(avvik)
    }
}
