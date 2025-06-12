package no.kartverket.nibas.nibasarbeidsliste.model

import java.time.LocalDate
import java.time.ZonedDateTime

data class NibasGrenseResponse(
    val innhold: List<NibasGrense>,
    val side: Int,
    val antallPerSide: Int,
    val totaltAntall: Int,
    val totaltAntallSider: Int
)

data class NibasKommune(
    val fylkesLokalID: String?,
    val kommuneLokalID: String,
    val kommunenummer: String,
    val kommunenavn: String
)

data class NibasGrense(
    val id: String,
    val lokalid: String,
    val grensetype: String,
    val geometri: NibasGeometri,
    val gyldighet: NibasGyldighet,
    val kommuner: List<NibasKommune>,
    val noeyaktighet: Int?,
    val maalemetode: String?,
    val datafangstdato: ZonedDateTime?,
    val foerstedigitaliseringsdato: ZonedDateTime,
    val opphav: String?,
    val informasjon: String?,
    val endretAv: String,
    val endretDato: ZonedDateTime,
    val typeEndring: String,
)

data class NibasGeometri(
    val type: String,
    val coordinates: List<List<Double>>
)

data class NibasGyldighet(
    val gyldigFra: LocalDate,
    val gyldigTil: LocalDate?
)


