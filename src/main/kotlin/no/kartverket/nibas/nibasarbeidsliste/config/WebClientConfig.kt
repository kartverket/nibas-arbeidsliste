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

    @Value("\${nibas.api.base-url}")
    private val baseUrl: String,

    @Value("\${api.key.matrikkel:#{null}}")
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

    @Bean
    fun nibasWebClient(): WebClient {
        val webClientBuilder = WebClient.builder()
            .baseUrl(baseUrl)

        // Legg til API-nøkkel kun hvis sikkerhet ikke er deaktivert
        if (!isSecurityDisabled() && !matrikkelApiKey.isNullOrBlank()) {
            logger.info("Konfigurerer WebClient med API-nøkkelautentisering")
            webClientBuilder.defaultHeader("X-API-Key", matrikkelApiKey)
        } else {
            logger.info("Kjører med sikkerhet deaktivert eller manglende API-nøkkel")
        }
        return webClientBuilder.build()
    }
}
