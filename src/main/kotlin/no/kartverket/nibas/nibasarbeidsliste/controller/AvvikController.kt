package no.kartverket.nibas.nibasarbeidsliste.controller

import no.kartverket.nibas.nibasarbeidsliste.api.AvvikApi
import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.KommuneAvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.service.AvvikService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api/v1/avvik")
class AvvikController(private val avvikService: AvvikService) : AvvikApi {
    private val logger = LoggerFactory.getLogger(AvvikController::class.java)

    /**
     * Henter en paginert liste med alle registrerte avvik.
     *
     * @param grensetyper Liste med grensetyper som skal inkluderes i søket
     * @param side Sidenummer for paginering (0-indeksert).
     * @param antall Maksimalt antall avvik per side.
     * @return En [ResponseEntity] som inneholder en [Page] med [AvvikDTO].
     */
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun hentAlleAvvik(
        @RequestParam(defaultValue = "Fylkesgrense,Kommunegrense") grensetyper: List<String>?,
        @RequestParam(defaultValue = "0") side: Int,
        @RequestParam(defaultValue = "10") antall: Int
    ): ResponseEntity<Page<AvvikDTO>> {
        logger.info("Henter avvik for side {} med antall {}", side, antall)
        val avvik = avvikService.hentAlleAvvik(grensetyper, PageRequest.of(side, antall))
        return ResponseEntity.ok(avvik)
    }

    /**
     * Henter alle avvik som er registrert for en spesifikk kommune.
     * @param grensetyper Liste med grensetyper som skal inkluderes i søket
     * @param lokalId Unik id for kommunen
     * @return En liste med [AvvikDTO] som representerer alle avvik knyttet til kommunen.
     *         Returnerer en tom liste hvis ingen avvik er registrert for kommunen.
     */
    @GetMapping(path = ["/{lokalId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun hentAvvik(@PathVariable lokalId: String, @RequestParam(defaultValue = "Fylkesgrense,Kommunegrense") grensetyper: List<String>?): ResponseEntity<List<AvvikDTO>> {
        logger.info("Henter avvik for lokalId {} med grensetyper {}", lokalId, grensetyper)
        val avvik = avvikService.hentAvvik(lokalId, grensetyper)
        return ResponseEntity.ok(avvik)
    }

    /**
     * Henter en oppsummering som viser antall avvik per kommune.
     * Returnerer en liste over kommuner som har minst ett avvik.
     * @param grensetyper Liste med grensetyper som skal inkluderes i søket
     * @param side Sidenummer for paginering.
     * @param antall Maksimalt antall kommuner per side.
     * @return En [ResponseEntity] som inneholder en liste med [KommuneAvvikDTO].
     */
    @GetMapping(path = ["/kommuner"], produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun hentKommunerMedAvvikSummary(
        @RequestParam(defaultValue = "Fylkesgrense,Kommunegrense") grensetyper: List<String>?,
        @RequestParam(defaultValue = "0") side: Int,
        @RequestParam(defaultValue = "10") antall: Int
    ): ResponseEntity<Page<KommuneAvvikDTO>> {
        logger.info("Liste med av kommuner som har avvik. Side $side, antall per side$antall")
        val summary = avvikService.hentKommunerMedAvvikSummary(grensetyper, PageRequest.of(side, antall))
        return ResponseEntity.ok(summary)
    }
}
