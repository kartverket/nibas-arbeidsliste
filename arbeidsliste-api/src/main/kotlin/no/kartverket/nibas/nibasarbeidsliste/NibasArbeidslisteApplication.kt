package no.kartverket.nibas.nibasarbeidsliste

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableResilientMethods
@EnableScheduling
class NibasArbeidslisteApplication

fun main(args: Array<String>) {
    runApplication<NibasArbeidslisteApplication>(*args)
}
