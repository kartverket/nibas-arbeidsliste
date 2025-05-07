-- Rådata for Matrikkel grensepunkt
CREATE TABLE nibas_arbeidsliste_schema.matrikkel_grensepunkt
(
    id                       BIGINT PRIMARY KEY,
    position_x               NUMERIC(38, 2),
    position_y               NUMERIC(38, 2),
    koordinatsystemkode_id   SMALLINT,
    grensemerke_nedsatt_i_id SMALLINT,
    grensepunkttype_id       SMALLINT,
    malemetode_id            SMALLINT,
    noyaktighet              INTEGER,
    datafangstdato           DATE,
    grensepunktnr            VARCHAR(255),
    oppdateringsdato         TIMESTAMP,
    batch_id                 BIGINT
);

-- Batch informasjon
CREATE TABLE nibas_arbeidsliste_schema.m22_data_batch
(
    id                  BIGSERIAL PRIMARY KEY,
    endrings_nummer     BIGINT    NOT NULL,
    download_timestamp  TIMESTAMP NOT NULL DEFAULT now(),
    description         VARCHAR(255),
    complete            BOOLEAN   NOT NULL DEFAULT FALSE,
    source_database_url VARCHAR(512)
);

-- Opprett indeks for primærnøkkel
CREATE INDEX idx_raw_grensepunkt_id ON nibas_arbeidsliste_schema.matrikkel_grensepunkt (id);
