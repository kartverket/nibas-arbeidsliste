# Koordinater og koordinatsystemer

Dette gjelder grenser og koordinater på det norske fastlandet.

## M22
M22 støtter flere koordinatsystemer og lagrer koordinater i databasen i følgende systemer:
- **UTM Sone 32N (EPSG:25832)**
- **UTM Sone 33N (EPSG:25833)**
- **UTM Sone 35N (EPSG:25835)**
* Koordinater lagres i meter

## NIBAS
NIBAS (både backend og frontend) bruker og lagrer koordinater i databasen med kun ett system:
- **UTM Sone 33N (EPSG:25833)**
* Koordinater lagres i meter

## Koordinatområder
Koordinatene bør normalt være mellom 0 og 1 000 000. Men siden NIBAS kun støtter ett koordinatsystem, kan noen koordinater i Norge falle utenfor dette området. Disse koordinatene kan lagres som:
- Negative tall
- Verdier over 1 000 000
Når de faller utenfor det definerte området for UTM33

## Oppførsel ved koordinattransformasjon
Ved transformasjon av koordinater mellom UTM-soner er det viktig å forstå følgende oppførsel:

1. **UTM-soneegenskaper**:
   - Hver UTM-sone har sin egen sentralmeridian (9°Ø for UTM32, 15°Ø for UTM33, 27°Ø for UTM35)
   - Hver sone bruker en "falsk østverdi" (false easting) på 500 000 meter for å sikre at alle koordinater er positive innenfor sonen

2. **Forventede resultater etter transformasjon**:
   - Koordinater fra UTM32 (Vestlandet) vil ofte bli negative når de transformeres til UTM33
   - Koordinater fra UTM35 (Østlandet) vil ofte overstige 1 000 000 meter når de transformeres til UTM33
   - Dette er normal og forventet oppførsel når man arbeider på tvers av UTM-soner

3. **Konsekvenser for lagring**:
   - Databasen må støtte negative tall og verdier større enn 1 000 000
   - Applikasjoner må kunne håndtere disse utvidede områdene for visning og beregninger
   - Romlige indekser og spørringer bør utformes med disse områdene i tankene

## Visning av M22-data
For å vise grenser fra M22 i NIBAS, må vi transformere alle ikke-UTM33-koordinater til UTM33:

1. Hvis koordinaten er i UTM33: Ingen transformasjon nødvendig
2. Hvis koordinaten er i UTM32: Transformer til UTM33 for å matche NIBAS sitt koordinatsystem
3. Hvis koordinaten er i UTM35: Transformer til UTM33 for å matche NIBAS sitt koordinatsystem

#