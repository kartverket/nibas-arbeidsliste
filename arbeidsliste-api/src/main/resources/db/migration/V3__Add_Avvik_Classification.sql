-- Change tolerance to double precision for distance
ALTER TABLE nibas_arbeidsliste_schema.avvik
    ALTER COLUMN tolerance TYPE DOUBLE PRECISION;

-- Add classification field to koordinater_med_avvik table
ALTER TABLE nibas_arbeidsliste_schema.koordinater_med_avvik
    ADD COLUMN er_paa_matrikkel_linje BOOLEAN;
