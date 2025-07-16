-- Clear all data from tables while preserving structure
-- Order matters due to foreign key constraints

-- DELETE FROM nibas_arbeidsliste_schema.koordinater_med_avvik;
-- DELETE FROM nibas_arbeidsliste_schema.grense_kommuner;
-- DELETE FROM nibas_arbeidsliste_schema.avvik;
-- DELETE FROM nibas_arbeidsliste_schema.raw_matrikkel_grenselinje;
-- DELETE FROM nibas_arbeidsliste_schema.raw_matrikkel_grensepunkt;
DELETE FROM nibas_arbeidsliste_schema.matrikkel_grenselinje;

-- Reset sequences to start from 1
-- ALTER SEQUENCE nibas_arbeidsliste_schema.avvik_id_seq RESTART WITH 1;
