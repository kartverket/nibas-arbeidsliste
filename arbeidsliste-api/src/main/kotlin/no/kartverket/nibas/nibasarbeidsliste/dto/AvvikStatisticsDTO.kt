package no.kartverket.nibas.nibasarbeidsliste.dto

import com.fasterxml.jackson.annotation.JsonProperty
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus

data class AvvikStatisticsDTO(
    @param:JsonProperty("totalAvvik")
    val totalAvvik: Long,

    @param:JsonProperty("statusCounts")
    val statusCounts: Map<AvvikStatus, Long>,

    @param:JsonProperty("arbeidsGrenser")
    val arbeidsGrenser: Long,

    @param:JsonProperty("arbeidsAvvikPunkter")
    val arbeidsAvvikPunkter: Long,

    @param:JsonProperty("arbeidsStatusCounts")
    val arbeidsStatusCounts: Map<AvvikStatus, Long>,

    @param:JsonProperty("grensetypeDetails")
    val grensetypeDetails: Map<String, GrensetypeStatisticsDTO>,

    @param:JsonProperty("kommunerMedAvvik")
    val kommunerMedAvvik: Long
)
