package no.kartverket.nibas.nibasarbeidsliste.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Model class representing a border (grense) from the Nibas API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GrenseResponse(
    val id: String,
    val lokalid: String,
    val grensetype: String,
    val geometri: Geometri,
    val gyldighet: Gyldighet,
    val datafangstdato: String?,
    val foerstedigitaliseringsdato: String?,
    val opphav: String?,
    val informasjon: String?,
    val endretAv: String,
    val endretDato: String,
    val typeEndring: String,
    val maalemetode: String,
    val noeyaktighet: Int
)

data class Geometri(
    val type: String,
    val coordinates: List<List<Double>>
)

data class Gyldighet(
    val gyldigFra: String,
    val gyldigTil: String?
)
