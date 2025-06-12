# Nibas Arbeidsliste

Microtjeneste for håndtering av avvik i grensedata mellom NIBAS og Matrikkelen.
Henter in grensedata fra NIBAS og Matrikkelen og lagrer avvik i database.

Prosjektet er satt som med moduler bestående av:

* [arbeidsliste-api](#Arbeidsliste-api): Api som servere avvik til nibas klient
* [m22-db-download](./m22-db-download/README.md): Modul som laster ned grensedata fra Matrikkel-databasen

## Arbeidsliste-api

1. **Sett opp NIBAS API lokalt**:
    - `git clone https://github.com/kartverket/nibas-backend.git`
    - Start NIBAS API lokalt på port 8080 med 'localhost,security-off' profil

2. **Konfigurer Arbeidsliste**:
    - Følg det lokale oppsettet for Arbeidsliste som beskrevet nedenfor
    - Sørg for at applikasjonen kjører med profilen `localhost,security-off`

3. **Database konfigurasjon**:
   Applikasjonen bruker Flyway for databasemigrasjoner. Dette sikrer at databaseskjemaet er konsistent på tvers av miljøer.

## Tech Stack

* Kotlin
* JDK 21
* Gradle
* Spring Boot
* REST-API
* PostgreSQL/PostGIS for geografiske data
* JPA/Hibernate Spatial
* Flyway for databasemigrasjoner

# Lokalt Oppsett (på egen maskin)

## Krever følgende installert:

* Java JDK 21
* PostgreSQL med PostGIS-utvidelse

## Database

Bruker NIBAS-databasen (`nibas`) med et eget dedikert schema (`nibas_arbeidsliste_schema`) og databasebruker (`nibas_arbeidsliste`).

Applikasjonen er avhengig av at PostGIS-utvidelsen er installert i `nibas`-databasen (vanligvis i `public`-schemaet).
Brukerens `search_path` settes slik at både det dedikerte schemaet og PostGIS-schemaet er inkludert.

### Opprett schema og bruker

Antar at nibas-backend database er opprettet og at PostGIS-utvidelsen er installert i `nibas`-databasen.

```bash
# Logg inn som postgres-bruker
sudo -u postgres psql -d nibas
```

```sql
-- 1. Opprett brukeren (passordet må matche application-localhost.yml)
CREATE
    USER nibas_arbeidsliste WITH PASSWORD 'nibas_arbeidsliste';

-- 2. Opprett schemaet og sett eierskap
CREATE SCHEMA nibas_arbeidsliste_schema AUTHORIZATION nibas_arbeidsliste;

-- 3. Gi brukeren tilgang til PostGIS-schemaet (antar 'public')
GRANT
    USAGE
    ON
    SCHEMA
    public TO nibas_arbeidsliste;

-- 4. Gi brukeren lesetilgang til nødvendige PostGIS-tabeller (antar 'public')
GRANT SELECT ON TABLE public.spatial_ref_sys TO nibas_arbeidsliste;

-- 5. Sett brukerens standard søkesti (search_path)
ALTER
    USER nibas_arbeidsliste SET search_path = nibas_arbeidsliste_schema, public;
```

*Merk: Hvis PostGIS er installert i et annet schema enn `public`, må du erstatte `public` med korrekt schema-navn i kommandoene over.*

### Verifiser tilkoblingen

Du kan teste tilkoblingen med den nye brukeren:

```bash
psql -U nibas_arbeidsliste -d nibas -h localhost
```

Når tilkoblet, kan du verifisere søkestien:

```sql
SHOW
    search_path;
-- Forventet output: "nibas_arbeidsliste_schema, public"
```

### Databasemigrasjoner med Flyway

Applikasjonen bruker Flyway for å håndtere databasemigrasjoner. Migrasjonsskriptene ligger i `src/main/resources/db/migration` og kjøres automatisk ved
oppstart.

Migrasjonsskriptene følger navnekonvensjonen `V{versjon}__{beskrivelse}.sql`. Applikasjonen bruker ett enkelt migrasjonsskript:

- `V1__Initial_Tabeller.sql` - Oppretter alle nødvendige tabeller og indekser

### Tøm databasedata

For å tømme all data fra tabellene uten å slette selve tabellene:

```bash
PGPASSWORD=nibas_arbeidsliste psql -h localhost -d nibas -U nibas_arbeidsliste -f clear_data.sql
```

### Databasekonfigurasjon (application-localhost.yml)

Standardinnstillingene for lokal utvikling (`localhost`-profilen) er definert i `src/main/resources/application-localhost.yml`:

- URL: `jdbc:postgresql://localhost:5432/nibas`
- Brukernavn: `nibas_arbeidsliste`
- Passord: `nibas_arbeidsliste`

## Lokal kjøring

For å kunne hente ut data fra NIBAS-backend, må du kjøre NIBAS-backend lokalt med profil `localhost,security-off`.
Se nibas-backend README for mer detaljer.

### IntelliJ

For å kjøre applikasjonen lokalt i IntelliJ:

1. Åpne Run/Debug Configurations dialog (Run -> Edit Configurations...)
2. Klikk på + i øvre venstre hjørne og velg "Application"
3. Sett "Main class" til `no.kartverket.nibas.nibasarbeidsliste.NibasArbeidslisteApplication`
4. I seksjonen Environment variables, klikk på + for å legge til en ny miljøvariabel.
   Sett "Name" til "ENV" og "Value" til "local".
5. Sett JDK til Java 21 (Temurin 21)
6. Legg til under "Active Profile": `localhost,security-off`
7. Trykk "Apply" og deretter "Ok"
8. Kjør applikasjonen ved å trykke på Run-knappen

### Kommandolinje

For å kjøre applikasjonen fra kommandolinjen:

```bash
./gradlew bootRun --args='--spring.profiles.active=localhost,security-off'
```

Applikasjonen vil starte på port 8082 med localhost-profilen, som definert i `application-localhost.yml`.

## API

http://localhost:8082/swagger-ui/index.html#/

* `GET /api/v1/avvik`: Henter alle avvik
* `GET /api/v1/avvik/kommuner`: Henter kommuner med avvik

## TODO:

### Setup tings...

- [x] SKIP oppsett. DEV
- [x] Auth mot NIBAS-backend.
- [x] Auth mot nibas-frontend via proxy.
    - [x] Proxy i nibas-backend som kaller arbeidsliste
- [x] Database DEV
    - [x] Lag bruker og schema for arbeidsliste i dev
    - [ ] Fyll opp DEV database med grenser.
- [ ] Database PROD
    - [ ] Lag bruker og schema for arbeidsliste i prod
    - [ ] Fyll opp PROD database med grenser.
- [ ] SKIP oppsett. PROD
- [ ] Tilgang til M22 database med de nyeste dataene.

### Funksjonalitet

- [x] Hente grenser fra NIBAS-backend
- [x] Hente grenser fra Matrikkelen
- [x] Lagre grenser fra M22 i arbeidsliste database
- [x] Endepunkt som viser M22 grenser til nibas klient.
- [x] Finn avvik i grenser mellom NIBAS og Matrikkelen
- [x] Lagre avvik mellom NIBAS-backend og Matrikkelen i database
- [ ] Fjern falske positive fra avvik. Beholder all avvik.
- [ ] Behendling av nye avvik. Ved kjøring av sammeligning av grenser vil oppstå dublikate avvik.
    - [ ] Ikke legg til nye avvik hvis det allerede finnes et avvik for grensen, hvis status er NY eller VENT. Sjekk per localID
    - [ ] Hvis status er LØST, så skal det opprettes et nytt avvik. Siden da er det feil under behandling eller det har kommet ett faktisk nytt på sammen
      grense.
    - [ ] 
- [ ] Lag endepunkt som viser antall avvik for analyse / dashboard
    - [ ] Hvor mange ekte og falske avvik
    - [ ] Hvor mange avvik per grensetype (kommunegrense etcc)
    - [ ] Hvor mange avvik løst. Total og per grensetype. 
    - [ ] Hvor mange kommuner med avvik og uten avvik.
- [ ] Endringlogg fra M22 til å oppdatere grenser
- [ ] Vis teiggrenser (samme prosess som for admn-grenser)
    - [ ] Hente teiggrens fra M22
    - [ ] Konverters til rikitig format
    - [ ] Lagre grenser i arbeidslist-db
    - [ ] Lag endpunkt som viser teiggrenser til klient
    - [ ] 
