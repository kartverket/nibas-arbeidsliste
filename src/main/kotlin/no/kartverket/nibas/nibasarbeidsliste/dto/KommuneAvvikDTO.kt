package no.kartverket.nibas.nibasarbeidsliste.dto

/**
 * DTO for kommuner med avvik
 */
data class KommuneAvvikDTO(
    val kommunenavn: String,
    val kommunenummer: String,
    val kommunelokalid: String?,
    val fylkeslokalid: String?,
    val antallAvvik: Int
)
