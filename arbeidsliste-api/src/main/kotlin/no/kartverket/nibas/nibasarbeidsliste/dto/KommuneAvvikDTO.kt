package no.kartverket.nibas.nibasarbeidsliste.dto

/**
 * DTO for kommuner med avvik
 */
data class KommuneAvvikDTO(
    val fylkesLokalID: String?,
    val kommuneLokalID: String?,
    val kommuneNummer: String,
    val kommuneNavn: String,
    val antallAvvik: Int
)
