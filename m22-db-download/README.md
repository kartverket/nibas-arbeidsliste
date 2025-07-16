# m22-db-download

Modul for å importere grensedata fra Matrikkel Oracle-database til PostgreSQL

## Oppsett

1. Endre navn på `gradle.properties.template` til `gradle.properties`
2. Fyll inn databasetilkoblingsdetaljer for både Matrikkel og arbeidsliste-databaser
3. **Viktig**: Start arbeidsliste-api Spring Boot-applikasjonen først for å opprette nødvendige tabeller:
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=localhost,security-off'
   ```

## Bruk

Kjør bulk-import av alle data:

```bash
./gradlew runBulkImportAll
```

Verifiser at importen fungerte:

```bash
./gradlew runVerifyImport
```

## Hva gjør den

- Henter siste endringsnummer fra M22 `ENDRING`
- Lagrer rådata i PostgreSQL-tabeller (`raw_matrikkel_grensepunkt`, `raw_matrikkel_grenselinje`)
- Lagrer endringsnummer-bookmark for changelog-synkronisering
