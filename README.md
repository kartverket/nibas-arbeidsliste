# Nibas Arbeidsliste

Microtjeneste for håndtering av avvik i grensedata mellom NIBAS og Matrikkelen.
Hent in grensedata fra NIBAS og Matrikkelen og lagrer avvik i database.

## API
* `GET /api/avvik`: Henter alle avvik

## Tech Stack
* Kotlin
* JDK 21
* Gradle
* Spring Boot
* REST-API
* PostgreSQL/PostGIS for geografiske data
* JPA/Hibernate Spatial


# Lokalt Oppsett (på egen maskin)
## Krevre folgend installer:
* Java JDK 21
* PostgreSQL med PostGIS-utvidelse

## Database
Applikasjonen bruker PostgreSQL med PostGIS-utvidelse for å håndtere geografiske data.
Applikasjonen forventer at PostgreSQL med PostGIS-utvidelsen er installert. Følg disse stegene for å sette opp databasen:

```sql
-- Opprett bruker og database (kjør som postgres-bruker)
CREATE USER arbeidsliste WITH PASSWORD 'arbeidsliste';
CREATE DATABASE arbeidsliste OWNER arbeidsliste;
GRANT ALL PRIVILEGES ON DATABASE arbeidsliste TO arbeidsliste;

-- Koble til databasen og aktiver PostGIS
\c arbeidsliste
CREATE EXTENSION postgis;
```

Databasekonfigurasjonen er definert i `application.yml`.


## Lokal kjøring
For å kunne hente ut data fra NIBAS-backend, må du kjøre NIBAS-backend lokalt med profil `localhost,security-off`.
Se nibas-backend README for mer detaljer.

### IntelliJ

For å kjøre applikasjonen lokalt i IntelliJ:

1. Åpne Run/Debug Configurations dialog (Run -> Edit Configurations...)
2. Klikk på + i øvre venstre hjørne og velg "Application"
3. I seksjonen Environment variables, klikk på + for å legge til en ny miljøvariabel.
   Sett "Name" til "ENV" og "Value" til "local".
4. Sett java-version som temurin21, og modul lik nibas-arbeidsliste.main. 
   Sett class lik: "no.kartverket.nibas.nibasarbeidsliste.NibasArbeidslisteApplication"
5. Legg til programargumenter: `--spring.profiles.active=localhost,security-off`
6. Trykk "Apply" og deretter "Ok"

### Kommandolinje

For å kjøre applikasjonen fra kommandolinjen:

```bash
./gradlew bootRun --args='--spring.profiles.active=localhost,security-off'
```

Applikasjonen vil starte på port 8082 med localhost-profilen.



## TODO:
### Setup tings..
- [ ] Dockerfile
- [ ] Docker compose (for å kjøre både applikasjonen og NIBAS-backend)
- [ ] .editorconfig (kopier fra nibas)  
- [ ] SKIP oppsett

### Funksjoner
#### Mock-API
- [x] GET-endepunkt med avvik
- [ ] Fylle opp mock-data avvik i database


#### Real-API 
- [x] Hente grenser fra NIBAS-backend
- [ ] Hente grenser fra Matrikkel
- [ ] Finn avvik i grenser mellom NIBAS og Matrikkelen
- [ ] Lagre avvik mellom NIBAS-backend og Matrikkelen i database