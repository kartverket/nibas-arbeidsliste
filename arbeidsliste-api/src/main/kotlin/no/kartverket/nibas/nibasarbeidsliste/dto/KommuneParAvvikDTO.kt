package no.kartverket.nibas.nibasarbeidsliste.dto


data class KommuneParAvvikDTO(
    val kommune1: KommuneDTO,
    val kommune2: KommuneDTO,
    val antallGrenserMedAvvik: Int,
    val antallPunkterMedAvvik: Int,
)
