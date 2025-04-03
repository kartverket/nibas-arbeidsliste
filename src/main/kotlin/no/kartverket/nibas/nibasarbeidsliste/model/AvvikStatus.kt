package no.kartverket.nibas.nibasarbeidsliste.model

// Forslag til forskjellige status for avvik
enum class AvvikStatus {
    // Nytt avvik registrert
    NY,

    // Holder kanksje med kun NY
    // Avviket er under behandling.
    UNDER_BEHANDLING,

    // Avviket er fikset (alle punkter er blitt helt like mellom NIBAS og Matrikkelen)
    FIKSET,

    // Avvik er stuet vekk og kan jobbes med senere.
    VENT,

    // False positive avvik
    AVVIST
}
