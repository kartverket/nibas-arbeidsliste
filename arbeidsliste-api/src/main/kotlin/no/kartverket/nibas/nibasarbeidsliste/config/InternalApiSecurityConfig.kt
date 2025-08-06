package no.kartverket.nibas.nibasarbeidsliste.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.filter.OncePerRequestFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Konfigurasjon for intern API-sikkerhet.
 *
 * Oppretter et filter som autentiserer forespørsler basert på en statisk API-nøkkel gitt i
 * X-API-Key-headeren.
 */
@Configuration
@EnableWebSecurity
@Profile("!security-off")
class InternalApiSecurityConfig(
    @Value("\${nibas.arbeidsliste.api-key}") private val expectedApiKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        log.info("Konfigurerer Spring Security med API-nøkkel autentisering")
        return http
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }
            .csrf { csrf -> csrf.disable() }
            .build()
    }

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
        val providedKey = request.getHeader(InternalApiSecurityConfig.API_KEY_HEADER)
        if (isValidApiKey(providedKey)) {
            log.debug("Gyldig API-nøkkel mottatt for: {}", request.servletPath)
            // Sender forespørselen videre til neste filter i rekkefølgen
            filterChain.doFilter(request, response)
        } else {
            log.warn("Ugyldig eller manglende API-nøkkel-forsøk for: {}", request.servletPath)
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("""{"error": "Unauthorized", "message": "Valid API key required"}""")
            return
        }
    }

    private fun isValidApiKey(providedKey: String?): Boolean {
        return expectedApiKey.isNotBlank() && expectedApiKey == providedKey
    }
}
