package no.kartverket.nibas.nibasarbeidsliste.service

import no.kartverket.nibas.nibasarbeidsliste.client.NibasClient
import no.kartverket.nibas.nibasarbeidsliste.model.NibasGrenseResponse
import org.springframework.stereotype.Service

/**
 * Simple service for accessing NIBAS grenser
 */
@Service
class NibasGrenseService(
    private val nibasClient: NibasClient
) {
    /**
     * Get paginated list of grenser
     * @param page Page number (1-based)
     * @param size Number of items per page (default 100)
     */
    fun getGrenser(page: Int = 0, size: Int = 100): NibasGrenseResponse {
        return nibasClient.getGrenser(page, size)
    }
}


fun main() {
    val client = NibasGrenseService(NibasClient("http://localhost:8080/v1", "123456"))
    val grenser = client.getGrenser(0, 1)
    println(grenser)
}
