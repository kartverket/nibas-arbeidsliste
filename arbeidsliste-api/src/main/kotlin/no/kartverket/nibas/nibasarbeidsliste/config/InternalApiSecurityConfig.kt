package no.kartverket.nibas.nibasarbeidsliste.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Konfigurasjon for intern API-sikkerhet.
 *
 * Oppretter et filter som autentiserer forespørsler basert på en statisk API-nøkkel gitt i
 * X-API-Key-headeren.
 */
@Configuration
@Profile("!security-off")
class InternalApiSecurityConfig(
    @Value("\${nibas.arbeidsliste.api-key}") private val expectedApiKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val API_KEY_HEADER = "X-API-Key"
    }

    /**
     * Oppretter et filter som autentiserer forespørsler basert på en statisk API-nøkkel gitt i
     * X-API-Key-headeren.
     *
     * @return [ApiKeyAuthFilter]
     */
    @Bean
    fun apiKeyAuthFilter(): ApiKeyAuthFilter {
        log.info("Oppretter ApiKeyAuthFilter for interne backend-kall.")
        if (expectedApiKey.isBlank()) {
            throw IllegalStateException("API-nøkkel for backend-kommunikasjon er ikke konfigurert")
        }
        return ApiKeyAuthFilter(expectedApiKey)
    }

    /**
     * Sett filter for interne API-path. Slik at det kun brukes for direkte backend-kall.
     *
     * @param filter [ApiKeyAuthFilter]
     * @return [FilterRegistrationBean<ApiKeyAuthFilter>]
     */
    @Bean
    fun apiKeyAuthFilterRegistration(
        filter: ApiKeyAuthFilter
    ): FilterRegistrationBean<ApiKeyAuthFilter> {
        val registration = FilterRegistrationBean(filter)
        // Bruk dette filteret KUN for interne API-stier kalt direkte av backend
        val internalUrlPattern = "/internal-api/*"
        registration.addUrlPatterns(internalUrlPattern)
        registration.setName("apiKeyAuthFilter")
        log.info("Registrerer ApiKeyAuthFilter for internt URL-mønster: {}", internalUrlPattern)
        return registration
    }
}

/**
 * Et Servlet-filter som autentiserer forespørsler basert på en statisk API-nøkkel gitt i
 * X-API-Key-headeren.
 */
class ApiKeyAuthFilter(private val expectedApiKey: String) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        log.debug("Gyldig API-nøkkel ${expectedApiKey}mottatt for: {}", request.servletPath)
        filterChain.doFilter(request, response)
    }
}
