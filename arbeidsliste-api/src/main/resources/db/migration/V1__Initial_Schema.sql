CREATE TABLE IF NOT EXISTS nibas_arbeidsliste_schema.avvik
(
    id                           BIGSERIAL PRIMARY KEY,
    registrert_dato              TIMESTAMP        NOT NULL,
    status                       VARCHAR(255)     NOT NULL,
    antall_koordinater           INTEGER,
    antall_koordinater_med_avvik INTEGER,
    tolerance                    DOUBLE PRECISION NOT NULL,
    grense_id                    VARCHAR(255),
    lokalid                      VARCHAR(255),
    grensetype                   VARCHAR(255),
    geometri                     geometry(LineString, 25833),
    gyldig_fra                   DATE,
    gyldig_til                   DATE,
    datafangstdato               VARCHAR(255),
    foerstedigitaliseringsdato   VARCHAR(255),
    opphav                       VARCHAR(255),
    informasjon                  VARCHAR(255),
    endret_av                    VARCHAR(255),
    endret_dato                  VARCHAR(255),
    type_endring                 VARCHAR(255),
    maalemetode                  VARCHAR(255),
    noeyaktighet                 INTEGER
);

CREATE INDEX IF NOT EXISTS idx_avvik_status ON nibas_arbeidsliste_schema.avvik (status);
CREATE INDEX IF NOT EXISTS idx_avvik_grensetype ON nibas_arbeidsliste_schema.avvik (grensetype);

CREATE TABLE IF NOT EXISTS nibas_arbeidsliste_schema.koordinater_med_avvik
(
    avvik_id                    BIGINT NOT NULL,
    koordinat_fra_nibas         geometry(Point, 25833),
    koordinat_fra_matrikkelen   geometry(Point, 25833),
    distanse_mellom_koordinater DOUBLE PRECISION,
    er_paa_matrikkel_linje      BOOLEAN,
    CONSTRAINT fk_koordinater_avvik FOREIGN KEY (avvik_id) REFERENCES nibas_arbeidsliste_schema.avvik (id)
);

CREATE TABLE IF NOT EXISTS nibas_arbeidsliste_schema.grense_kommuner
(
    avvik_id        BIGINT NOT NULL,
    fylkes_lokalid  VARCHAR(255),
    kommune_lokalid VARCHAR(255),
    kommunenummer   VARCHAR(255),
    kommunenavn     VARCHAR(255),
    CONSTRAINT fk_kommune_avvik FOREIGN KEY (avvik_id) REFERENCES nibas_arbeidsliste_schema.avvik (id)
);

CREATE INDEX IF NOT EXISTS idx_kommune_lokalid ON nibas_arbeidsliste_schema.grense_kommuner (kommune_lokalid);
CREATE INDEX IF NOT EXISTS idx_kommune_nummer ON nibas_arbeidsliste_schema.grense_kommuner (kommunenummer);


CREATE TABLE IF NOT EXISTS nibas_arbeidsliste_schema.raw_matrikkel_grenselinje
(
    id                        BIGINT PRIMARY KEY,
    hjelpelinjetypeid         INTEGER,
    omtvistet                 BOOLEAN,
    folgerterrengdetaljid     INTEGER,
    administrativgrensekodeid INTEGER,
    malemetodeid              INTEGER,
    noyaktighet               INTEGER,
    datafangstdato            DATE,
    informasjon               TEXT,
    lagretnoyaktighetsklasse  INTEGER,
    kommunenrstrengcache      VARCHAR(255),
    versjon                   BIGINT,
    kurvesegmenttype          VARCHAR(50),
    kurvekoordinatsystemkode  INTEGER,
    kurvestartpunktid         BIGINT,
    kurveendpunktid           BIGINT,
    kurvebuepunktx            DOUBLE PRECISION,
    kurvebuepunkty            DOUBLE PRECISION,
    kurvepositions            TEXT,
    oppdateringsdato          TIMESTAMP,
    oppdatertav               VARCHAR(255),
    versjonid                 BIGINT NOT NULL
);

COMMENT ON TABLE nibas_arbeidsliste_schema.raw_matrikkel_grenselinje IS 'Raw grenselinjedata fra Matrikkel Oracle DB, brukt for synkronisering og konvertering';
COMMENT ON COLUMN nibas_arbeidsliste_schema.raw_matrikkel_grenselinje.versjonid IS 'Versjons-ID for inkrementell synkronisering fra Matrikkel-endringslogg';

CREATE INDEX IF NOT EXISTS idx_raw_matrikkel_grenselinje_versjonid ON nibas_arbeidsliste_schema.raw_matrikkel_grenselinje (versjonid);
CREATE INDEX IF NOT EXISTS idx_raw_matrikkel_grenselinje_admin_grense ON nibas_arbeidsliste_schema.raw_matrikkel_grenselinje (administrativgrensekodeid);


CREATE TABLE IF NOT EXISTS nibas_arbeidsliste_schema.raw_matrikkel_grensepunkt
(
    id                    BIGINT PRIMARY KEY,
    positionx             DOUBLE PRECISION NOT NULL,
    positiony             DOUBLE PRECISION NOT NULL,
    koordinatsystemkodeid INTEGER          NOT NULL,
    grensemerkenedsattiid INTEGER,
    grensepunkttypeid     INTEGER,
    malemetodeid          INTEGER,
    noyaktighet           INTEGER,
    datafangstdato        DATE,
    grensepunktnr         VARCHAR(255),
    kommunenrstrengcache  VARCHAR(255),
    versjon               BIGINT,
    oppdateringsdato      TIMESTAMP,
    oppdatertav           VARCHAR(255),
    versjonid             BIGINT,
    uuid                  CHAR(36)
);

COMMENT ON TABLE nibas_arbeidsliste_schema.raw_matrikkel_grensepunkt IS 'Raw grensepunktsdata fra Matrikkel Oracle DB';
CREATE INDEX IF NOT EXISTS idx_raw_matrikkel_grensepunkt_coords ON nibas_arbeidsliste_schema.raw_matrikkel_grensepunkt (positionx, positiony);


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
    kommunenavn1               VARCHAR(100),
    kommunenavn2               VARCHAR(100),
    informasjoncache           VARCHAR,
    versjon                    BIGINT,
    versjon_id                 INTEGER,
    oppdatert_av               VARCHAR
);

CREATE INDEX IF NOT EXISTS idx_matrikkel_grenselinje_id ON nibas_arbeidsliste_schema.matrikkel_grenselinje (id);
CREATE INDEX IF NOT EXISTS idx_matrikkel_grenselinje_kommune1 ON nibas_arbeidsliste_schema.matrikkel_grenselinje (kommunenr1);
CREATE INDEX IF NOT EXISTS idx_matrikkel_grenselinje_kommune2 ON nibas_arbeidsliste_schema.matrikkel_grenselinje (kommunenr2);

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


CREATE TABLE IF NOT EXISTS nibas_arbeidsliste_schema.kommune_lookup
(
    kommunenr   VARCHAR(4) PRIMARY KEY,
    kommunenavn VARCHAR(100) NOT NULL
);

COMMENT ON TABLE nibas_arbeidsliste_schema.kommune_lookup IS 'Oppslagstabell for å konvertere kommunenummer til kommunenavn';
COMMENT ON COLUMN nibas_arbeidsliste_schema.kommune_lookup.kommunenr IS '4-sifret kommunenummer (f.eks. "0301")';
COMMENT ON COLUMN nibas_arbeidsliste_schema.kommune_lookup.kommunenavn IS 'Offisielt kommunenavn';

CREATE INDEX IF NOT EXISTS idx_kommune_lookup_kommunenr ON nibas_arbeidsliste_schema.kommune_lookup (kommunenr);

CREATE TABLE IF NOT EXISTS nibas_arbeidsliste_schema.matrikkel_endringsnummer
(
    endringsnummer   BIGINT PRIMARY KEY,
    oppdateringsdato TIMESTAMP NOT NULL
);

COMMENT ON TABLE nibas_arbeidsliste_schema.matrikkel_endringsnummer IS 'Holder styr på siste kjørte endringsnummer fra Matrikkel-endringslogg for inkrementell synkronisering.';

CREATE OR REPLACE FUNCTION nibas_arbeidsliste_schema.transform_point_to_utm33(
    x DOUBLE PRECISION,
    y DOUBLE PRECISION,
    srid_code INTEGER
) RETURNS GEOMETRY AS
$$
BEGIN
    RETURN ST_MakePoint(
            ROUND(ST_X(ST_Transform(ST_SetSRID(ST_MakePoint(x, y),
                                               CASE srid_code
                                                   WHEN 10 THEN 25832
                                                   WHEN 11 THEN 25833
                                                   WHEN 13 THEN 25835
                                                   ELSE 25833
                                                   END
                                    ), 25833))::numeric, 2),
            ROUND(ST_Y(ST_Transform(ST_SetSRID(ST_MakePoint(x, y),
                                               CASE srid_code
                                                   WHEN 10 THEN 25832
                                                   WHEN 11 THEN 25833
                                                   WHEN 13 THEN 25835
                                                   ELSE 25833
                                                   END
                                    ), 25833))::numeric, 2)
           );
END;
$$ LANGUAGE plpgsql IMMUTABLE;
