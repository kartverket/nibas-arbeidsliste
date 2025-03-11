package no.kartverket.nibas.nibasarbeidsliste.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class AvvikDTO(
    @JsonProperty("id")
    val id: Long? = null,

    @JsonProperty("registrertDato")
    val registrertDato: LocalDateTime? = null,

    @JsonProperty("status")
    val status: AvvikStatus = AvvikStatus.NY,

    @JsonProperty("harGrenseJson")
    val harGrenseJson: Boolean = false,

    @JsonProperty("grenseJson")
    val grenseJson: String? = null
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
