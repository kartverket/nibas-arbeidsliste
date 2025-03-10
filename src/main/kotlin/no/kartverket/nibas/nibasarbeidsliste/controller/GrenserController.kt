package no.kartverket.nibas.nibasarbeidsliste.controller

import no.kartverket.nibas.nibasarbeidsliste.service.NibasGrenserService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/grenser")
class GrenserController(private val nibasGrenserService: NibasGrenserService) {
    private val logger = LoggerFactory.getLogger(GrenserController::class.java)

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentGrenser(
        @RequestParam(defaultValue = "1") side: Int,
        @RequestParam(defaultValue = "10") antall: Int
    ): Mono<String> {
        logger.info("Mottok forespørsel om å hente grenser med side={} og antall={}", side, antall)
        return nibasGrenserService.hentGrenser(side, antall)
    }

    @GetMapping("/test", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun testGrenser(): String {
        logger.info("Starter test av grenser-endepunkt")
        nibasGrenserService.testHentGrenser()
        return "Test av grenser-endepunkt startet. Sjekk loggene for resultatet."
    }
}
