package no.kartverket.nibas.nibasarbeidsliste.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Nibas Arbeidsliste API")
                    .description("API som serverer avvik mellom administrative grenser i NIBAS og Matrikkelen")
                    .version("v1.0.0")
                    .contact(
                        Contact()
                            .name("Kartverket")
                            .url("https://kartverket.no")
                    )
            )
            .addServersItem(
                Server()
                    .url("/")
                    .description("Default Server URL")
            )
    }
}
