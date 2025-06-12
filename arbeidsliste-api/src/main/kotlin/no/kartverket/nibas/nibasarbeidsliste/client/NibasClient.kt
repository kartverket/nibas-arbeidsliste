package no.kartverket.nibas.nibasarbeidsliste.client

import no.kartverket.nibas.nibasarbeidsliste.model.NibasGrenseResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class NibasClient(
    @Value("\${nibas.api.base-url}") private val baseUrl: String,
    @Value("\${api.key.matrikkel}") private val apiKey: String
) {
    private val restTemplate = RestTemplate()

    fun getGrenser(page: Int = 0, size: Int = 100): NibasGrenseResponse {
        val url = "$baseUrl/ekstern/grenser?side=$page&antall=$size"

        val headers = HttpHeaders().apply {
            set("Authorization", "Bearer $apiKey")
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

fun main() {
    val client = NibasClient("http://localhost:8080/v1", "123456")
    val grenser = client.getGrenser()
    println(grenser)
}
