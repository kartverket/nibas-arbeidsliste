package no.kartverket.nibas.nibasarbeidsliste.service

import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@SpringBootTest
@ActiveProfiles("security-off")
class NibasGrenserServiceTest {

    private val logger = LoggerFactory.getLogger(NibasGrenserServiceTest::class.java)

    @Autowired
    private lateinit var nibasGrenserService: NibasGrenserService

    @Test
    fun `test hent grenser`() {
        logger.info("Tester henting av grenser fra Nibas API...")

        try {
            // Hent grenser med side=2 og antall=5
            val response = nibasGrenserService.hentGrenser(2, 5)
                .block(Duration.ofSeconds(10)) // Blokkerer med timeout på 10 sekunder

            logger.info("=== GRENSER HENTET FRA API ====")
            logger.info(response ?: "Ingen respons mottatt")
            logger.info("=== SLUTT PÅ RESPONS ====")
        } catch (e: Exception) {
            logger.error("Feil ved henting av grenser: {}", e.message, e)
        }
    }

    @Test
    fun `test direkte webclient kall`() {
        logger.info("Tester direkte kall til Nibas API...")

        try {
            val webClient = WebClient.builder()
                .baseUrl("http://localhost:8080/v1")
                .build()

            val response = webClient.get()
                .uri("/grenser?side=2&antall=5")
                .retrieve()
                .bodyToMono(String::class.java)
                .block(Duration.ofSeconds(10))

            logger.info("=== DIREKTE KALL RESPONS ====")
            logger.info(response ?: "Ingen respons mottatt")
            logger.info("=== SLUTT PÅ RESPONS ====")
        } catch (e: Exception) {
            logger.error("Feil ved direkte kall: {}", e.message, e)
        }
    }
}
