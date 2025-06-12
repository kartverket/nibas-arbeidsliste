-- Clear all data from tables while preserving structure
-- Order matters due to foreign key constraints

DELETE FROM nibas_arbeidsliste_schema.koordinater_med_avvik;
DELETE FROM nibas_arbeidsliste_schema.grense_kommuner;
DELETE FROM nibas_arbeidsliste_schema.avvik;

-- Reset sequences to start from 1
ALTER SEQUENCE nibas_arbeidsliste_schema.avvik_id_seq RESTART WITH 1;