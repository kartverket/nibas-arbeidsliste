DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM information_schema.columns
                   WHERE table_schema = 'nibas_arbeidsliste_schema'
                     AND table_name = 'avvik'
                     AND column_name = 'tolerance'
                     AND data_type = 'integer') THEN
            ALTER TABLE nibas_arbeidsliste_schema.avvik
                ALTER COLUMN tolerance TYPE DOUBLE PRECISION;
        END IF;
    END
$$;

DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1
                       FROM information_schema.columns
                       WHERE table_schema = 'nibas_arbeidsliste_schema'
                         AND table_name = 'koordinater_med_avvik'
                         AND column_name = 'er_paa_matrikkel_linje') THEN
            ALTER TABLE nibas_arbeidsliste_schema.koordinater_med_avvik
                ADD COLUMN er_paa_matrikkel_linje BOOLEAN;
        END IF;
    END
$$;
