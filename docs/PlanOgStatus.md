# TODOs for Kvalitetsheving delleveranse

## Ting som må avklares

### Hvor my avvik kan det være mellom NIBAS og M22

Per nå er plan å finne avvik på mer enn 10cm.
Men hvor mye skal det være tilatt på sikt.
Er det noen lov som

### Status

Per nå har vi følgende status for avvik:

* Ny -- Nytt avvik registrert
* Under behandling -- Avviket er under behandling. Kanskje holder det med kun NY. Men det kan brukes hvis man ikke rekker å bli ferdig med å fikse avviket i én
  omgang.
* Fikset -- Avviket er fikset (alle punkter er blitt helt like mellom NIBAS og Matrikkelen)
* Vent -- Avviket er satt på vent og kan jobbes med senere.
* Avvist -- Falskt positivt avvik. Siden dette ikke burde skje, kan denne statusen kanskje fjernes.

### Dokumentasjon. Done

Note: M22 har dokumentasjon på alt. Nibas må bare følge m22.

Må få avklart om vi trenger å tilrettelegge for dokumentasjon av avvik.
Ved å lage "Se liste med publiserte utkast" vil man ha dokumentasjon i NIBAS over hvilke avvik som er rettet.
(Kan det med fordel lages en standardisert prosess for retting av avvik som brukerne må følge?)

### Hvilke avvik som skal rettes

Per nå har det blitt sagt at kun avvik i kommune- og fylkesgrenser skal rettes.
Men det er mange avvik i de andre grensetypene: riksgrense, territorialgrense og avtalt avgrensningsgrense.
Det kan enkelt legges til filtrering på hvilke avvik som finnes per grensetype.
Avvikene for alle grensetypene finnes allerede, så dette er et rent fagspørsmål.
Teknisk sett burde alle adm. grenser i M22 og NIBAS være så like som mulig.

### Vis kun kommunegrenser-verktøy. Done.

Note: Per nå trengs det ikke. Kan lages ved brukerbehov.

Avgjøre om dette trengs.
Blir automatisk hentet ved retting av avvik. Trengs dette som et eget verktøy for å se adm. grenser fra M22?
Bruker har nevnt "at dette kunne være kjekt", men usikkert om de mente kun når de jobber med avvik, eller også som ett eget verktøy.

### Nøyaktighet

Bruker er veldig opptatt av at nibas skal være 1 til 1 med M22 som for eksempel nøyaktighet.
Men det er usikkert hvorfor og hva nøyaktighet brukes til i NIBAS.
Og dett gir ikke så mye mening, side ett linjestykke i NIBAS og kan være ett eller flere linjestykker i M22 (og i teorien omvendt, men mest andre veien).

### M22 vs NIBAS

Hva er egentlig forholdet mellom i NIBAS.
Hva sier loven om hva nibas er?

### Brukeropplevelse avvikslist

Dette er bare hypoteser som om hva som kan gi en bedre brukeropplevelse.
Dette er relativt enkelt å implementere.

* Tydeliggjøre hvor i kommunen avviket er. Done.
* Oversikt over alle avvik og status, inkludert antall løste avvik.
    * F.eks. antall avvik etc.
* Vurdere V2 (se Figma) av avvikslisten, eller er den god nok som den er.
* Søk etter kommune (navn/nummer) i avvikslisten.
* Arbeidsflyt. Opprette avvik, fikse avvik, se list med publiseret retta avvik.
* Se liste med publiseret retta avvik. (Alleredre påbegynt sak TS-2152)

## Implementasjon detaljer

### Funksjonalitet

* [x] Hente grenser fra NIBAS-backend
* [x] Hente grenser fra Matrikkelen
* [x] Lagre grenser fra M22 i arbeidsliste database
* [x] Endepunkt som viser M22-grenser til NIBAS-klienten.
* [x] Finn avvik i grenser mellom NIBAS og Matrikkelen
* [x] Lagre avvik mellom NIBAS-backend og Matrikkelen i database
* [x] Falske positiver fjernet fra avvik (alle avvik beholdes).
* [x] Lag endepunkt som viser antall avvik for analyse / dashboard
    * [x] Hvor mange ekte og falske avvik
    * [x] Hvor mange avvik per grensetype (kommunegrense etc.)
    * [x] Antall løste avvik, totalt og per grensetype.
    * [x] Hvor mange kommuner med avvik og uten avvik.
* [x] Endringslogg fra M22 til å oppdatere admn grenser
    * [x] Kjør Endringslogg
    * [x] Oppdater grenser.
    * [x] Admn grenser
* [x] Behandling av nye avvik. Ved kjøring av sammenligning av grenser kan duplikate avvik oppstå.
    * [x] Ikke legg til nye avvik hvis det allerede finnes et avvik for grensen med status NY eller VENT (sjekk per localId).
    * [x] Hvis status er LØST, skal det opprettes et nytt avvik. Dette fanger opp feil under retting eller nye, faktiske avvik på samme grense.
* [ ] Vis teiggrenser (samme prosess som for admn-grenser)
    * [ ] Hente teiggrens fra M22
    * [ ] Konverter til riktig format
    * [ ] Lagre grenser i arbeidslist-db
    * [ ] Endringslogg teiggrenser
    * [ ] Lag endpunkt som viser teiggrenser til klient
* Bytt ut eksiterende teiggrense verktøy med nytt fra arbeidsliste.

### Setup tings

* [x] SKIP oppsett. DEV
* [x] Auth mot NIBAS-backend.
* [x] Auth mot nibas-frontend via proxy.
    * [x] Proxy i nibas-backend som kaller arbeidsliste
* [x] Database DEV
    * [x] Lag bruker og schema for arbeidsliste i dev
    * [ ] Fyll opp DEV database med grenser.
* [ ] Database PROD
* [ ] Tilgang til M22 database med de nyeste dataene.
    * [ ] Lag bruker og schema for arbeidsliste i prod
    * [ ] Fyll opp PROD database med grenser.
* [ ] SKIP oppsett. PROD
