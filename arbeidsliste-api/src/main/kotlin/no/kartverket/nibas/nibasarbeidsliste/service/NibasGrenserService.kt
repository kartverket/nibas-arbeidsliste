package no.kartverket.nibas.nibasarbeidsliste.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Service
class NibasGrenserService(
    private val webClient: WebClient
) {

    private val logger = LoggerFactory.getLogger(NibasGrenserService::class.java)

    /**
     * Henter grenser fra Nibas API
     * @param side Sidenummer for paginering
     * @param antall Antall resultater per side
     * @return JSON-respons som String
     */
    fun hentGrenser(side: Int = 1, antall: Int = 10): Mono<String> {
        logger.info("Henter grenser fra Nibas API med side={} og antall={}", side, antall)

        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/ekstern/grenser")
                    .queryParam("side", side)
                    .queryParam("antall", antall)
                    .build()
            }
            .retrieve()
            .bodyToMono(String::class.java)
            .doOnSuccess { response ->
                logger.info("Mottok svar fra Nibas API: {}", response)
            }
            .doOnError { error ->
                logger.error("Feil ved henting av grenser fra Nibas API: {}", error.message, error)
            }
    }

    /**
     * Henter en spesifikk grense fra Nibas API basert på lokalid
     * @param lokalid Lokalid for grensen som skal hentes
     * @return JSON-respons som String
     */
    fun hentGrenseByLokalId(lokalid: String): Mono<String> {
        logger.info("Henter grense fra Nibas API med lokalid={}", lokalid)

        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/ekstern/grenser/{lokalid}")
                    .build(lokalid)
            }
            .retrieve()
            .bodyToMono(String::class.java)
            .doOnSuccess { response ->
                logger.info("Mottok svar fra Nibas API for grense med lokalid={}: {}", lokalid, response)
            }
            .doOnError { error ->
                logger.error("Feil ved henting av grense med lokalid={} fra Nibas API: {}", lokalid, error.message, error)
            }
    }
}
