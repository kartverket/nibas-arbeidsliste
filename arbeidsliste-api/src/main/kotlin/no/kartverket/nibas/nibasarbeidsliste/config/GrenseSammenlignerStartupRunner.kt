package no.kartverket.nibas.nibasarbeidsliste.config

import no.kartverket.nibas.nibasarbeidsliste.service.GrenseSammenlignerService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class GrenseSammenlignerStartupRunner(
    private val grenseSammenlignerService: GrenseSammenlignerService,
    @param:Value("\${grense-sammenligner.run-on-startup:false}")
    private val runOnStartup: Boolean,
    @param:Value("\${grense-sammenligner.tolerance-meter:2.0}")
    private val toleranceMeter: Double
) : CommandLineRunner {

    companion object {
        private val log = LoggerFactory.getLogger(GrenseSammenlignerStartupRunner::class.java)
    }

    override fun run(vararg args: String?) {
        if (runOnStartup) {
            log.info("Running grense sammenligner on startup with tolerance $toleranceMeter meters")
            grenseSammenlignerService.finnAvvik(toleranceMeter)
        }
    }
}
