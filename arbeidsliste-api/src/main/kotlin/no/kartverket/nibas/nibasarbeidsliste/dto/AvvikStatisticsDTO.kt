package no.kartverket.nibas.nibasarbeidsliste.dto

import com.fasterxml.jackson.annotation.JsonProperty
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus

data class AvvikStatisticsDTO(
    @JsonProperty("totalAvvik")
    val totalAvvik: Long,

    @JsonProperty("totalHelperPunkter")
    val totalHelperPunkter: Long,

    @JsonProperty("statusCounts")
    val statusCounts: Map<AvvikStatus, Long>,

    @JsonProperty("arbeidsGrenser")
    val arbeidsGrenser: Long,

    @JsonProperty("arbeidsAvvikPunkter")
    val arbeidsAvvikPunkter: Long,

    @JsonProperty("arbeidsHelperPunkter")
    val arbeidsHelperPunkter: Long,

    @JsonProperty("arbeidsStatusCounts")
    val arbeidsStatusCounts: Map<AvvikStatus, Long>,

    @JsonProperty("grensetypeDetails")
    val grensetypeDetails: Map<String, GrensetypeStatisticsDTO>,

    @JsonProperty("kommunerMedAvvik")
    val kommunerMedAvvik: Long
)
