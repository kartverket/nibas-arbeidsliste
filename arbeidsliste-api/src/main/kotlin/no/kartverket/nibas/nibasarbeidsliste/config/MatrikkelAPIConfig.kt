package no.kartverket.nibas.nibasarbeidsliste.config

import jakarta.xml.ws.BindingProvider
import no.kartverket.nibas.matrikkel.api.domain.MatrikkelContext
import no.kartverket.nibas.matrikkel.api.domain.geometri.koder.KoordinatsystemKodeId
import no.kartverket.nibas.matrikkel.api.service.endringslogg.EndringsloggService
import no.kartverket.nibas.matrikkel.api.service.endringslogg.EndringsloggServiceWS
import no.kartverket.nibas.matrikkel.api.service.kommune.KommuneService
import no.kartverket.nibas.matrikkel.api.service.kommune.KommuneServiceWS
import no.kartverket.nibas.matrikkel.api.service.matrikkelenhet.MatrikkelenhetService
import no.kartverket.nibas.matrikkel.api.service.matrikkelenhet.MatrikkelenhetServiceWS
import no.kartverket.nibas.matrikkel.api.service.store.StoreService
import no.kartverket.nibas.matrikkel.api.service.store.StoreServiceWS
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MatrikkelAPIConfig(
    @param:Value("\${matrikkelen.urlEndringslogg}") private val apiUrl: String,
    @param:Value("\${matrikkelen.username}") private val apiUser: String,
    @param:Value("\${matrikkelen.password}") private val apiPass: String
) {

    private fun <T> configurePort(port: T, servicePath: String): T {
        (port as BindingProvider).apply {
            requestContext[BindingProvider.ENDPOINT_ADDRESS_PROPERTY] = "$apiUrl/$servicePath"
            requestContext[BindingProvider.USERNAME_PROPERTY] = apiUser
            requestContext[BindingProvider.PASSWORD_PROPERTY] = apiPass
        }
        return port
    }

    @Bean
    fun endringsloggService(): EndringsloggService =
        configurePort(EndringsloggServiceWS().endringsloggServicePort, "EndringsloggServiceWS")

    @Bean
    fun storeService(): StoreService =
        configurePort(StoreServiceWS().storeServicePort, "StoreServiceWS")

    @Bean
    fun matrikkelenhetService(): MatrikkelenhetService =
        configurePort(MatrikkelenhetServiceWS().matrikkelenhetServicePort, "MatrikkelenhetServiceWS")

    @Bean
    fun kommuneService(): KommuneService =
        configurePort(KommuneServiceWS().kommuneServicePort, "KommuneServiceWS")

    @Bean
    fun matrikkelContext(): MatrikkelContext = MatrikkelContext(
        "nb_NO",
        true,
        KoordinatsystemKodeId(11), // EUREF89 UTM sone 33
        "4.13.1.0",
        "nibas_arbeidsliste",
        null
    )
}
