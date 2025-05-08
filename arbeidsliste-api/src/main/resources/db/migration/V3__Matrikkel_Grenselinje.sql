-- V3__Matrikkel_Grenselinje.sql
-- Table for storing grenselinje (boundary line) data migrated from Oracle

CREATE TABLE IF NOT EXISTS nibas_arbeidsliste_schema.matrikkel_grenselinje (
    id BIGINT PRIMARY KEY,
    hjelpelinjetype_id SMALLINT,
    omtvistet BOOLEAN,
    folgerterrengdetalj_id SMALLINT,
    administrativgrensekode_id SMALLINT,
    malemetode_id SMALLINT,
    noyaktighet INTEGER,
    datafangstdato DATE,
    lagretnoyaktighetsklasse SMALLINT,
    geom geometry(LineString, 25833),
    oppdateringsdato TIMESTAMP(6)
);

CREATE INDEX IF NOT EXISTS idx_matrikkel_grenselinje_id
  ON nibas_arbeidsliste_schema.matrikkel_grenselinje (id);
