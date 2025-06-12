CREATE TABLE IF NOT EXISTS nibas_arbeidsliste_schema.matrikkel_grenselinje
(
    id                         BIGINT PRIMARY KEY,
    hjelpelinjetype_id         SMALLINT,
    omtvistet                  BOOLEAN,
    folgerterrengdetalj_id     SMALLINT,
    administrativgrensekode_id SMALLINT,
    malemetode_id              SMALLINT,
    noyaktighet                INTEGER,
    datafangstdato             DATE,
    lagretnoyaktighetsklasse   SMALLINT,
    geom                       geometry(LineString, 25833),
    oppdateringsdato           TIMESTAMP(6),
    kommunenr1                 VARCHAR(4),
    kommunenr2                 VARCHAR(4),
    informasjoncache           VARCHAR,
    versjon                    BIGINT,
    versjon_id                 INTEGER,
    oppdatert_av               VARCHAR
);

CREATE TABLE IF NOT EXISTS nibas_arbeidsliste_schema.matrikkel_endringsnummer
(
    id               SERIAL PRIMARY KEY,
    endringsnummer   BIGINT    NOT NULL UNIQUE,
    oppdateringsdato TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_matrikkel_grenselinje_id
    ON nibas_arbeidsliste_schema.matrikkel_grenselinje (id);


CREATE INDEX IF NOT EXISTS idx_matrikkel_grenselinje_kommune1
    ON nibas_arbeidsliste_schema.matrikkel_grenselinje (kommunenr1);

CREATE INDEX IF NOT EXISTS idx_matrikkel_grenselinje_kommune2
    ON nibas_arbeidsliste_schema.matrikkel_grenselinje (kommunenr2);

DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1
                       FROM information_schema.table_constraints
                       WHERE table_schema = 'nibas_arbeidsliste_schema'
                         AND table_name = 'matrikkel_grenselinje'
                         AND constraint_name = 'chk_kommuner') THEN
            ALTER TABLE nibas_arbeidsliste_schema.matrikkel_grenselinje
                ADD CONSTRAINT chk_kommuner
                    CHECK (kommunenr1 IS NOT NULL AND (kommunenr2 IS NULL OR kommunenr2 != kommunenr1));
        END IF;
    END
$$;
