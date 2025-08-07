# Lib - Matrikkel Integrasjon

Biblioteker for integrasjon med Matrikkel-tjenester.

## Moduler

### matrikkel-api
Genererte Java-klasser fra Matrikkel WSDL-filer for SOAP-kall til Matrikkel-tjenester:
- EndringsloggService - Henter endringslogg fra Matrikkel
- KommuneService - Kommune-data
- MatrikkelenhetService - Matrikkelenheter 
- StoreService - Lagring og henting av objekter

### matrikkel-changelog
Kotlin-bibliotek for å arbeide med Matrikkel endringslogg:
- `MatrikkelChangelog` - Hovedklasse for endringslogg-operasjoner

### prebuilt
Pre-bygde JAR-filer for Matrikkel API integrasjon.