package no.kartverket.nibas.nibasarbeidsliste.client

import no.kartverket.nibas.nibasarbeidsliste.model.NibasGrenseResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class NibasClient(
    private val environment: Environment,
    @Value("\${nibas.api.base-url}") private val baseUrl: String,
    @Value("\${api.key.matrikkel:#{null}}") private val apiKey: String?
) {
    private val restTemplate = RestTemplate()

    /**
     * Bestemmer om sikkerhet er deaktivert i gjeldende miljø
     * Sikkerhet er kun deaktivert i localhost-miljø med security-off profil
     */
    private fun isSecurityDisabled(): Boolean {
        val activeProfiles = environment.activeProfiles
        return activeProfiles.contains("localhost") && activeProfiles.contains("security-off")
    }

    fun getGrenser(page: Int = 0, size: Int = 100): NibasGrenseResponse {
        val url = "$baseUrl/ekstern/grenser?side=$page&antall=$size"

        // Valider API-nøkkel hvis sikkerhet ikke er deaktivert
        if (!isSecurityDisabled() && apiKey.isNullOrBlank()) {
            throw IllegalArgumentException("API key is not set and security is not disabled")
        }

        val headers = HttpHeaders().apply {
            // Legg til auth header bare hvis sikkerhet ikke er deaktivert
            if (!isSecurityDisabled() && !apiKey.isNullOrBlank()) {
                set("Authorization", "Basic $apiKey")
            }
            set("Accept", "application/json")
        }

        val request = HttpEntity<Any>(headers)

        val response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            request,
            NibasGrenseResponse::class.java
        )
        return response.body ?: throw RuntimeException("Failed to get response from NIBAS API")
    }
}
