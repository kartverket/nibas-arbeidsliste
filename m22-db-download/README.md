# m22-db-download

Koden her er kun ment til å kjøre lokalt for å fylle opp arbeidsliste-databasen.
Koden bevares hvis man av noen grunnskulle trenge å gjøre det på nytt.

Modulen er ansvarlig for å laste ned og konvertere grensedata fra Matrikkel-databasen til et optimalisert format for NIBAS-systemet.

## Oppsett

1. Rename `gradle.properties.template` til `gradle.properties`
2. Fyll ut matrikkel databasen man ønsker å hente data fra og nibas-arbeidsliste databasen man ønsker å lagre data i.

Last ned data fra matrikkel DB, konverter data og fyll nibas-arbeidsliste DB med data:

```bash
./gradlew downloadConvertFillDB
```

Hvis man trenger å legge til flere kolonner kan man legge til i FlatBuffer skjemaen og kjøre:

```bash
./gradlew generateFlatbuffers
```

### Funksjonalitet

- **Nedlasting av data**: Henter grensepunkter, grenselinjer og teiger fra Matrikkel-databasen
- **Konvertering**: Transformerer datastrukturer fra Matrikkelens format til NIBAS' format
- **Optimalisering**: Bruker FlatBuffers for effektiv datarepresentasjon og tilgang
- **Lagring**: Lagrer konverterte data i binærfiler for videre prosessering

### Komponenter

1. **MatrikkelDownload.kt**: Hovedklasse for nedlasting av data fra Matrikkel-databasen
    - Inneholder SQL-spørringer for å hente grensepunkter, grenselinjer og teiger
    - Implementerer effektiv nedlasting med buffering og parallell prosessering
    - Håndterer koordinattransformasjoner og skalering

2. **Convert.kt**: Konverterer data fra Matrikkel-format til NIBAS-format
    - Transformerer geometriske strukturer (punkter, linjer, polygoner)
    - Optimaliserer datarepresentasjon for NIBAS-systemet

3. **ConvertedFile.kt**: Håndterer lagring og lesing av konverterte data
    - Implementerer effektiv indeksering for rask tilgang til data
    - Støtter paginering for håndtering av store datamengder

### Datamodell

Modulen bruker FlatBuffers for å definere datastrukturer:

- **MatrikkelDB.fbs**: Definerer strukturer for Matrikkel-data
- **Nibas.fbs**: Definerer strukturer for NIBAS-systemet
