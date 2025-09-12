package no.kartverket.nibas.nibasarbeidsliste

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// Test
// Test
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@EnableScheduling
class NibasArbeidslisteApplication

fun main(args: Array<String>) {
    runApplication<NibasArbeidslisteApplication>(*args)
}
