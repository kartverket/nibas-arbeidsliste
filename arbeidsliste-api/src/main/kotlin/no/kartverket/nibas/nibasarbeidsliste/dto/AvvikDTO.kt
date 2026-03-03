package no.kartverket.nibas.nibasarbeidsliste.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus
import java.time.LocalDate
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class AvvikDTO(
    @param:JsonProperty("id")
    val id: Long? = null,

    @param:JsonProperty("registrertDato")
    val registrertDato: LocalDateTime? = null,

    @param:JsonProperty("status")
    val status: AvvikStatus = AvvikStatus.NY,

    @param:JsonProperty("harGeometri")
    val harGeometri: Boolean = false,

    // Grense fields
    @param:JsonProperty("grenseId")
    val grenseId: String? = null,

    @param:JsonProperty("lokalId")
    val lokalId: String? = null,

    @param:JsonProperty("grensetype")
    val grensetype: String? = null,

    @param:JsonProperty("geometri")
    val geometri: GeoJsonLineString? = null,

    @param:JsonProperty("gyldigFra")
    val gyldigFra: LocalDate? = null,

    @param:JsonProperty("gyldigTil")
    val gyldigTil: LocalDate? = null,

    @param:JsonProperty("datafangstdato")
    val datafangstdato: String? = null,

    @param:JsonProperty("foerstedigitaliseringsdato")
    val foerstedigitaliseringsdato: String? = null,

    @param:JsonProperty("opphav")
    val opphav: String? = null,

    @param:JsonProperty("informasjon")
    val informasjon: String? = null,

    @param:JsonProperty("endretAv")
    val endretAv: String? = null,

    @param:JsonProperty("endretDato")
    val endretDato: String? = null,

    @param:JsonProperty("typeEndring")
    val typeEndring: String? = null,

    @param:JsonProperty("maalemetode")
    val maalemetode: String? = null,

    @param:JsonProperty("noeyaktighet")
    val noeyaktighet: Int? = null,

    @param:JsonProperty("antallKoordinater")
    val antallKoordinater: Int?,

    @param:JsonProperty("antallKoordinaterMedAvvik")
    val antallKoordinaterMedAvvik: Int?,

    @param:JsonProperty("koordinaterMedAvvik")
    val koordinaterMedAvvik: List<KoordinaterMedAvvikDTO>?,

    @param:JsonProperty("tolerance")
    val tolerance: Double?,

    @param:JsonProperty("kommuner")
    val kommuner: List<KommuneDTO>? = null,
)

data class GeoJsonLineString(
    @param:JsonProperty("type")
    val type: String = "LineString",

    @param:JsonProperty("coordinates")
    val coordinates: List<List<Double>>
)

data class GeoJsonPoint(
    @param:JsonProperty("type")
    val type: String = "Point",

    @param:JsonProperty("coordinates")
    val coordinates: List<Double>
)

data class KoordinaterMedAvvikDTO(
    @param:JsonProperty("nibasKoordinat")
    val nibasKoordinat: GeoJsonPoint,

    @param:JsonProperty("matrikkelKoordinat")
    val matrikkelKoordinat: GeoJsonPoint,

    @param:JsonProperty("distanseMellomKoordinater")
    val distanseMellomKoordinater: Double? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KommuneDTO(
    @param:JsonProperty("fylkesLokalID")
    val fylkesLokalID: String? = null,

    @param:JsonProperty("kommuneLokalID")
    val kommuneLokalID: String? = null,

    @param:JsonProperty("kommunenummer")
    val kommuneNummer: String? = null,

    @param:JsonProperty("kommunenavn")
    val kommuneNavn: String? = null
)
