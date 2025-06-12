package no.kartverket.nibas.nibasarbeidsliste.controller

import no.kartverket.nibas.nibasarbeidsliste.api.GrenseSammenlignerApi
import no.kartverket.nibas.nibasarbeidsliste.service.GrenseSammenlignerService
import no.kartverket.nibas.nibasarbeidsliste.service.GrenseSammenligningResultat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class GrenseSammenlignerController(
    private val sammenligner: GrenseSammenlignerService
) : GrenseSammenlignerApi {

    override fun sammenlignAlleGrenser(toleranseMeter: Double): ResponseEntity<GrenseSammenligningResultat> {
        val resultat = sammenligner.finnAvvik(toleranseMeter)
        return ResponseEntity.ok(resultat)
    }
}
