package no.kartverket.nibas.nibasarbeidsliste.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class GrensetypeStatisticsDTO(
    @JsonProperty("antallGrenserMedAvvik")
    val antallGrenserMedAvvik: Long,

    @JsonProperty("antallEkteAvvikPunkter")
    val antallEkteAvvikPunkter: Long,

    @JsonProperty("antallHelperPunkter")
    val antallHelperPunkter: Long
)
