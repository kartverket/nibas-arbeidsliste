package no.kartverket.nibas.nibasarbeidsliste.config

import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus
import no.kartverket.nibas.nibasarbeidsliste.repository.AvvikRepository
import no.kartverket.nibas.nibasarbeidsliste.service.NibasGrenserService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Komponent som initialiserer testdata for avvik ved oppstart av applikasjonen
 * Kjører kun i localhost-profilen for å unngå å generere testdata i produksjon
 */
@Component
@Profile("localhost")
class DataInitializer(
    private val avvikRepository: AvvikRepository,
    private val nibasGrenserService: NibasGrenserService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // lokalID til grenser i NIBAS som har avvik
    private val lokalIDs = arrayOf(
        "7bcca8e4-718e-396d-8493-8f374c5d12fd",
        "98304de1-663e-327e-9c73-ed350156e67c",
        "edc00dab-bbbb-3987-876d-e5233dbcb08c",
        "6c3e890d-2915-3453-a7fb-4dd86c56f0de",
    )

    @EventListener(ApplicationReadyEvent::class)
    fun initData() {
        if (avvikRepository.count() > 0) {
            logger.info("Database har allerede {} avvik, hopper over initialisering", avvikRepository.count())
            return
        }

        logger.info("Starter initialisering av testdata for avvik fra Nibas API...")

        try {
            val avvik = hentAvvikFraNibas()
            avvikRepository.saveAll(avvik)
            logger.info("Initialisert {} avvik i databasen", avvik.size)
        } catch (e: Exception) {
            logger.error("Feil ved initialisering av testdata: {}", e.message, e)
        }
    }

    /**
     * Henter grenser fra Nibas API basert på lokalID-er og oppretter avvik for hver grense
     */
    private fun hentAvvikFraNibas(): List<Avvik> {
        val avvikListe = mutableListOf<Avvik>()

        for (lokalId in lokalIDs) {
            logger.info("Henter grense med lokalID={} fra Nibas API", lokalId)

            try {
                val grenseJson = nibasGrenserService.hentGrenseByLokalId(lokalId)
                    .doOnError { error ->
                        logger.error("Feil ved henting av grense med lokalID={}: {}", lokalId, error.message, error)
                    }
                    .blockOptional()
                    .orElse(null)

                if (grenseJson != null) {
                    logger.info("Opprettet avvik for grense med lokalID={}", lokalId)

                    val avvik = Avvik(
                        grenseJson = grenseJson,
                        registrertDato = LocalDateTime.now(),
                        status = AvvikStatus.NY
                    )

                    avvikListe.add(avvik)
                } else {
                    logger.warn("Kunne ikke hente grense med lokalID={} fra Nibas API", lokalId)
                }
            } catch (e: Exception) {
                logger.error("Feil ved behandling av grense med lokalID={}: {}", lokalId, e.message, e)
            }
        }

        return avvikListe
    }
}
