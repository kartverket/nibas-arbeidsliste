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

Applikasjonen bruker PostgreSQL med PostGIS-utvidelse for å håndtere geografiske data.
Følg disse stegene for å sette opp databasen:

### Opprett database og aktiver PostGIS

```bash
# Logg inn som postgres-bruker
sudo -u postgres psql

# Kjør følgende SQL-kommandoer i psql-terminalen:
CREATE USER arbeidsliste WITH PASSWORD 'arbeidsliste';
CREATE DATABASE arbeidsliste OWNER arbeidsliste;
GRANT ALL PRIVILEGES ON DATABASE arbeidsliste TO arbeidsliste;

# Koble til databasen
\c arbeidsliste

# Aktiver PostGIS-utvidelsen
CREATE EXTENSION postgis;

# Avslutt psql
\q
```

### 4. Verifiser tilkoblingen

```bash
# Test tilkoblingen med den nye brukeren
psql -U arbeidsliste -d arbeidsliste -h localhost
```

Databasekonfigurasjonen er definert i `application.yml`. Standardinnstillingene er:

- URL: `jdbc:postgresql://localhost:5432/arbeidsliste`
- Brukernavn: `arbeidsliste`
- Passord: `arbeidsliste`

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
