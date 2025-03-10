package no.kartverket.nibas.nibasarbeidsliste.controller

import ArbeidslisteApi
import no.kartverket.nibas.nibasarbeidsliste.service.ArbeidslisteService
import org.springframework.web.bind.annotation.RestController

@RestController("ArbeidslisteController")
class ArbeidslisteController(private val arbeidslisteService: ArbeidslisteService) : ArbeidslisteApi {

    override fun getArbeidsliste(): String {
        return arbeidslisteService.getArbeidsliste()
    }
}
