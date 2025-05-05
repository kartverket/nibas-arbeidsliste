CREATE TABLE nibas_arbeidsliste_schema.avvik (
    id BIGSERIAL PRIMARY KEY,
    registrert_dato TIMESTAMP NOT NULL,
    status VARCHAR(255) NOT NULL,
    antall_koordinater INTEGER,
    antall_koordinater_med_avvik INTEGER,
    tolerance INTEGER NOT NULL,
    grense_id VARCHAR(255),
    lokalid VARCHAR(255),
    grensetype VARCHAR(255),
    geometri geometry(LineString, 25833),
    gyldig_fra DATE,
    gyldig_til DATE,
    datafangstdato VARCHAR(255),
    foerstedigitaliseringsdato VARCHAR(255),
    opphav VARCHAR(255),
    informasjon VARCHAR(255),
    endret_av VARCHAR(255),
    endret_dato VARCHAR(255),
    type_endring VARCHAR(255),
    maalemetode VARCHAR(255),
    noeyaktighet INTEGER
);

CREATE TABLE nibas_arbeidsliste_schema.koordinater_med_avvik (
    avvik_id BIGINT NOT NULL,
    koordinat_fra_nibas geometry(Point, 25833),
    koordinat_fra_matrikkelen geometry(Point, 25833),
    distanse_mellom_koordinater DOUBLE PRECISION,
    CONSTRAINT fk_koordinater_avvik FOREIGN KEY (avvik_id) REFERENCES nibas_arbeidsliste_schema.avvik(id)
);

CREATE TABLE nibas_arbeidsliste_schema.grense_kommuner (
    avvik_id BIGINT NOT NULL,
    fylkes_lokalid VARCHAR(255),
    kommune_lokalid VARCHAR(255),
    kommunenummer VARCHAR(255),
    kommunenavn VARCHAR(255),
    CONSTRAINT fk_kommune_avvik FOREIGN KEY (avvik_id) REFERENCES nibas_arbeidsliste_schema.avvik(id)
);

CREATE INDEX idx_avvik_status ON nibas_arbeidsliste_schema.avvik(status);
CREATE INDEX idx_avvik_grensetype ON nibas_arbeidsliste_schema.avvik(grensetype);
CREATE INDEX idx_kommune_lokalid ON nibas_arbeidsliste_schema.grense_kommuner(kommune_lokalid);
CREATE INDEX idx_kommune_nummer ON nibas_arbeidsliste_schema.grense_kommuner(kommunenummer);
