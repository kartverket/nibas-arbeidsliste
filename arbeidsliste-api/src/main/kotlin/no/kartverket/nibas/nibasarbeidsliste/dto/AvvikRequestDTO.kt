package no.kartverket.nibas.nibasarbeidsliste.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus

@JsonIgnoreProperties(ignoreUnknown = true)
data class AvvikRequestDTO(
    @param:JsonProperty("id")
    val id: Long,

    @param:JsonProperty("status")
    val status: AvvikStatus,

    )

@JsonIgnoreProperties(ignoreUnknown = true)
data class BulkAvvikRequestDTO(
    @param:JsonProperty("avvikUpdates")
    val avvikUpdates: List<AvvikRequestDTO>
)
