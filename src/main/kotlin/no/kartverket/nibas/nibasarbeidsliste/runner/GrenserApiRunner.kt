package no.kartverket.nibas.nibasarbeidsliste.runner

import no.kartverket.nibas.nibasarbeidsliste.service.NibasGrenserService
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Kjøres kun når profilen "test-api" er aktiv
 */
@Component
@Profile("test-api")
class GrenserApiRunner(private val nibasGrenserService: NibasGrenserService) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(GrenserApiRunner::class.java)

    override fun run(vararg args: String) {
        logger.info("Starter test av Nibas grenser API...")

        try {
            logger.info("Henter grenser fra endepunkt: /grenser?side=2&antall=5")

            val response = nibasGrenserService.hentGrenser(2, 5)
                .block()
            logger.info("=== GRENSER RESPONS ===")
            logger.info("{}", response ?: "Ingen respons mottatt")
            logger.info("=== SLUTT PÅ RESPONS ===")
        } catch (e: Exception) {
            logger.error("Feil ved henting av grenser: {}", e.message, e)
        }

        logger.info("Test fullført")
    }
}
