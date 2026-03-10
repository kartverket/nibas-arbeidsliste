package no.kartverket.nibas.nibasarbeidsliste.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig(
    @Value("\${nibas.api.base-url}")
    private val baseUrl: String,
) {
    @Bean
    fun nibasWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(baseUrl).build()
    }
}
