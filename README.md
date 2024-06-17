# Nibas Arbeidsliste

Spring Native applikasjon som leser matrikkelen sin endringslogg og tilbyr den til Nibas.

## Innholdsfortegnelse

- [Oppsett](#oppsett)

# Oppsett <a name="oppsett"></a>

For å kjøre applikasjonen lokalt trenger du å opprette en run-configuration i Intellij som setter miljøvariablelen "ENV=local". Dette vil gi litt penere logger i runtime:

1. Åpne prosjektet i Intellij.

2. Gå til Run -> Edit Configurations... fra toppmenyen. I dialogboksen Run/Debug Configurations, klikk på +-knappen eller velg typen "Spring".

3. Gi konfigurasjonen et navn (f.eks. NibasArbeidslisteApplicationLocal).
I seksjonen Environment variables, klikk på + for å legge til en ny miljøvariabel.
Sett "Name" til "ENV" og "Value" til "local".
4. Sett java-version som termurin21, og modul lik nibas-arbeidsliste.main. Sett class lik: "no.kartverket.nibas.nibasarbeidsliste.NibasArbeidslisteApplication"
5. Trykk "Apply" og deretter "Ok"