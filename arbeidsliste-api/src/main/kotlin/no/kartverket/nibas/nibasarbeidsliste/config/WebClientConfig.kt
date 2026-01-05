package no.kartverket.nibas.nibasarbeidsliste.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig(
    private val environment: Environment,

    @param:Value("\${nibas.api.base-url}")
    private val baseUrl: String,

    @param:Value("\${api.key.matrikkel:#{null}}")
    private val matrikkelApiKey: String?
) {
    private val logger = LoggerFactory.getLogger(WebClientConfig::class.java)

    /**
     * Bestemmer om sikkerhet er deaktivert i gjeldende miljø
     */
    private fun isSecurityDisabled(): Boolean {
        val activeProfiles = environment.activeProfiles
        return activeProfiles.contains("localhost") && activeProfiles.contains("security-off")
    }
    /**
     * Konfigurerer WebClient for NIBAS API-integrasjon.
     * Håndterer to scenarioer:
     * 1. Produksjonsmiljø: Krever gyldig API-nøkkel for autentisering
     * 2. Lokalt utviklingsmiljø: Kan kjøre uten sikkerhet når 'localhost,security-off' profiler er aktive
     */
    @Bean
    fun nibasWebClient(): WebClient {
        val webClientBuilder = WebClient.builder()
            .baseUrl(baseUrl)

        if (!isSecurityDisabled() && matrikkelApiKey.isNullOrBlank()) {
            throw IllegalArgumentException("matrikkelApiKey is not set and security is not disabled")
        }

        if (!isSecurityDisabled() && !matrikkelApiKey.isNullOrBlank()) {
            val authHeaderValue = "Basic $matrikkelApiKey"
            logger.info("Konfigurerer WebClient med IKKE-STANDARD 'Authorization: Basic <raw_key>' header")
            webClientBuilder.defaultHeader("Authorization", authHeaderValue)
        } else {
            logger.info("Kjører med sikkerhet deaktivert eller manglende API-nøkkel")
        }
        return webClientBuilder.build()
    }
}
