package no.kartverket.nibas.nibasarbeidsliste.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Security configuration that disables all security when the 'security-off' profile is active.
 * For use during local development.
 */
@Configuration
@EnableWebSecurity
@Profile("security-off")
@Order(1)
class SecurityOffConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        log.warn("SECURITY IS DISABLED - 'security-off' profile is active")

        return http
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }
            .csrf { csrf -> csrf.disable() }
            .headers { headers ->
                headers.frameOptions { frameOptions ->
                    frameOptions.disable()
                }
            }
            .build()
    }
}
