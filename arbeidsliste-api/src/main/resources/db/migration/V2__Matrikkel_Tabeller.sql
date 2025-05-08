-- Raw data for Matrikkel boundary points
CREATE TABLE IF NOT EXISTS nibas_arbeidsliste_schema.matrikkel_grensepunkt (
    id BIGINT PRIMARY KEY,
    position_x DOUBLE PRECISION NOT NULL,
    position_y DOUBLE PRECISION NOT NULL,
    koordinatsystemkode_id SMALLINT,
    grensemerke_nedsatt_i_id SMALLINT,
    grensepunkttype_id SMALLINT,
    malemetode_id SMALLINT,
    noyaktighet INTEGER,
    datafangstdato DATE,
    grensepunktnr VARCHAR(255),
    oppdateringsdato TIMESTAMP(6)
);

CREATE INDEX IF NOT EXISTS idx_matrikkel_grensepunkt_id
  ON nibas_arbeidsliste_schema.matrikkel_grensepunkt (id);
