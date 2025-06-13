package no.kartverket.nibas.nibasarbeidsliste.controller

import no.kartverket.nibas.nibasarbeidsliste.api.StatisticsApi
import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikStatisticsDTO
import no.kartverket.nibas.nibasarbeidsliste.service.StatisticsService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/statistikk")
class StatisticsController(private val statisticsService: StatisticsService) : StatisticsApi {

    private val logger = LoggerFactory.getLogger(StatisticsController::class.java)

    /**
     * Henter statistikk over avvik i systemet.
     *
     * @return En [ResponseEntity] som inneholder [AvvikStatisticsDTO] med oversikt over avvik
     */
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun hentAvvikStatistikk(): ResponseEntity<AvvikStatisticsDTO> {
        logger.info("Henter avvik statistikk")
        val statistikk = statisticsService.getAvvikStatistics()
        return ResponseEntity.ok(statistikk)
    }
}
