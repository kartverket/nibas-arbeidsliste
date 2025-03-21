package no.kartverket.nibas.nibasarbeidsliste.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus
import java.time.LocalDate
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class AvvikDTO(
    @JsonProperty("id")
    val id: Long? = null,

    @JsonProperty("registrertDato")
    val registrertDato: LocalDateTime? = null,

    @JsonProperty("status")
    val status: AvvikStatus = AvvikStatus.NY,

    @JsonProperty("harGeometri")
    val harGeometri: Boolean = false,

    // Grense fields
    @JsonProperty("grenseId")
    val grenseId: String? = null,

    @JsonProperty("lokalId")
    val lokalId: String? = null,

    @JsonProperty("grensetype")
    val grensetype: String? = null,

    @JsonProperty("geometri")
    val geometri: GeoJsonLineString? = null,

    @JsonProperty("gyldigFra")
    val gyldigFra: LocalDate? = null,

    @JsonProperty("gyldigTil")
    val gyldigTil: LocalDate? = null,

    @JsonProperty("datafangstdato")
    val datafangstdato: String? = null,

    @JsonProperty("foerstedigitaliseringsdato")
    val foerstedigitaliseringsdato: String? = null,

    @JsonProperty("opphav")
    val opphav: String? = null,

    @JsonProperty("informasjon")
    val informasjon: String? = null,

    @JsonProperty("endretAv")
    val endretAv: String? = null,

    @JsonProperty("endretDato")
    val endretDato: String? = null,

    @JsonProperty("typeEndring")
    val typeEndring: String? = null,

    @JsonProperty("maalemetode")
    val maalemetode: String? = null,

    @JsonProperty("noeyaktighet")
    val noeyaktighet: Int? = null,

    @JsonProperty("antallKoordinater")
    val antallKoordinater: Int?,

    @JsonProperty("antallKoordinaterMedAvvik")
    val antallKoordinaterMedAvvik: Int?,

    @JsonProperty("koordinaterMedAvvik")
    val koordinaterMedAvvik: List<KoordinaterMedAvvikDTO>?,

    @JsonProperty("tolerance")
    val tolerance: Int?,

    )

data class GeoJsonLineString(
    @JsonProperty("type")
    val type: String = "LineString",

    @JsonProperty("coordinates")
    val coordinates: List<List<Double>>
)

data class GeoJsonPoint(
    @JsonProperty("type")
    val type: String = "Point",

    @JsonProperty("coordinates")
    val coordinates: List<Double>
)

data class KoordinaterMedAvvikDTO(
    @JsonProperty("nibasKoordinat")
    val nibasKoordinat: GeoJsonPoint,

    @JsonProperty("matrikkelKoordinat")
    val matrikkelKoordinat: GeoJsonPoint
)
