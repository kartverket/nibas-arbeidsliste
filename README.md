# Nibas Arbeidsliste

Microtjeneste for håndtering av avvik i grensedata mellom NIBAS og Matrikkelen.
Hent in grensedata fra NIBAS og Matrikkelen og lagrer avvik i database.

## Mock oppsett

**Autentisering mot NIBAS-backend er ikke implementert ennå, så det er viktig å kjøre NIBAS-backend med "security-off" profilen.**

Mock dataen er grenser i nibas som vi vet har avvik mot Matrikkelen.

For å kjøre applikasjonen med mock-data, følg disse stegene:

1. **Sett opp NIBAS API lokalt**:
    - Klon NIBAS backend fra GitHub: `git clone https://github.com/kartverket/nibas-backend.git`
    - Bytt til branch: `git checkout TS-1761-nytt-endepunkt`
    - Start NIBAS API lokalt på port 8080 med 'localhost,security-off' profil

2. **Konfigurer Arbeidsliste**:
    - Følg det lokale oppsettet for Arbeidsliste som beskrevet nedenfor
    - Sørg for at applikasjonen kjører med profilen `localhost,security-off`

3. **Mock-data konfigurasjon**:
    - Mock-data blir hentet fra NIBAS API basert på lokalIDer definert i `DataInitializer.kt`
    - Du kan endre listen med lokalIDer i `DataInitializer.kt` for å hente andre grenser

4. **Database konfigurasjon**:
   I `application.yml` kan du konfigurere om databasen skal resettes ved oppstart:
   ```yaml
   spring:
     jpa:
       hibernate:
         # Database resetter seg hver gang applikasjonen startes
         ddl-auto: create-drop
         # ELLER: Database beholder data mellom omstarter
         # ddl-auto: none
   ```

Ved oppstart vil applikasjonen automatisk hente grensedata fra NIBAS API for de definerte lokalIDene og lagre dem som avvik i databasen. Disse avvikene kan
deretter vises via API-endepunktet `GET /api/v1/avvik`.

## Tech Stack

* Kotlin
* JDK 21
* Gradle
* Spring Boot
* REST-API
* PostgreSQL/PostGIS for geografiske data
* JPA/Hibernate Spatial

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

- [ ] SKIP oppsett. Smia-apps oppsett.
- [ ] Auth mot NIBAS-backend.
- [ ] Auth mot nibas-frontend via proxy.
- [ ] Database DEV
- [ ] Database PROD

### Funksjoner

#### Mock-API

- [x] GET-endepunkt med avvik
- [x] Hent en grense fra NIBAS-backend via lokalID
- [x] Fylle opp mock-data "avvik" i database

#### Real-API

- [x] Hente grenser fra NIBAS-backend
- [ ] Hente grenser fra Matrikkelen
- [ ] Finn avvik i grenser mellom NIBAS og Matrikkelen
- [ ] Lagre avvik mellom NIBAS-backend og Matrikkelen i database
