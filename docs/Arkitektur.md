

# Arkitektur

```mermaid
flowchart TD
    %% External Systems
    M22Log[M22 Endringslogg / API]

    %% NIBAS System
    subgraph NIBAS["NIBAS System"]
        subgraph Apps
            Frontend["nibas-frontend\nReact"]
            Backend["nibas-backend\nKotlin,Spring"]
            Worker["nibas-arbeidsliste\nKotlin"]
        end

        subgraph DBs
            NibasDB[(NIBAS Database\nPostgreSQL/PostGIS)]
            ArbeidslisteDB[(Arbeidsliste DB\nPostgreSQL/PostGIS)]
        end
    end

    %% User
    User[Sluttbruker] -->|Bruker| Frontend

    %% Data Flows
    Frontend <-->|API-kall| Backend
    Backend <-->|Leser/skriver| NibasDB

    Worker -->|Lytter og henter data| M22Log
    Worker <-->|Lagrer/henter| ArbeidslisteDB
    Worker -->|Varsler| Backend

    %% Styling
    classDef external fill:#f9f2d9,stroke:#f0ad4e
    classDef app fill:#d9edf7,stroke:#5bc0de
    classDef db fill:#dff0d8,stroke:#5cb85c

    class M22Log external
    class Frontend,Backend,Worker app
    class NibasDB,ArbeidslisteDB db
```

## Komponenter

* **Nibas-arbeidsliste**
    * Henter grenser fra M22 og lager i nibas-arbeidslist database. Fra endringslogg
    * Finner avvik mellom M22 og NIBAS og lager i nibas-arbeidslist database
    * Sender avvik til nibas-fronted via proxy i nibas-backend

* **nibas-backend**
    * Proxy for nibas-arbeidsliste
    * Autentisering/autorisasjon
    * Dataformatering
    * har tilgang til nibas-db, som har all info om grense til nibas systemt

* **nibas-frontend**
    * Viser avvik til sluttbruker
    * Interaktivt grensesnitt
    * Sender fikset grense til nibas-backend, som oppdaterer grense i nibas-db

* **M22-endringlogg**
    * Nibas-arbeidlis lytter på endringslogg til M22
    * Oppdater M22 grenser i nibas-arbeidsliste database ved behov.


## Dataflyt
1. Nibas-arbeidsliste henter og prosesserer data fra M22
2. Avvik sendes til nibas-backend
3. Frontend henter og viser data via backend
