package no.kartverket.nibas.nibasarbeidsliste.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class GrensetypeStatisticsDTO(
    @param:JsonProperty("antallGrenserMedAvvik")
    val antallGrenserMedAvvik: Long,

    @param:JsonProperty("antallAvvikPunkter")
    val antallAvvikPunkter: Long
)
