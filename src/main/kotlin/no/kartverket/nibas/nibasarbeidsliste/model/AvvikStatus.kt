package no.kartverket.nibas.nibasarbeidsliste.model

// Forslag til forskjellige status for avvik
enum class AvvikStatus {
    // Nytt avvik registrert
    NY,

    // Avviket er under behandling.
    UNDER_BEHANDLING,

    // Avviket er fikset (alle punkter er blitt helt like mellom NIBAS og Matrikkelen)
    FIKSET,

    // Avvik er nedprioritert og kan bli fikset senere
    NEDPRIORITERT,

    // False positive avvik
    AVVIST
}
