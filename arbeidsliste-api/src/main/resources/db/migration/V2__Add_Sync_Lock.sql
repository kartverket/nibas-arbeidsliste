CREATE TABLE IF NOT EXISTS nibas_arbeidsliste_schema.sync_lock
(
    id           INTEGER PRIMARY KEY DEFAULT 1,
    locked_until TIMESTAMP,
    CONSTRAINT single_row CHECK (id = 1)
);

INSERT INTO nibas_arbeidsliste_schema.sync_lock (id)
VALUES (1)
ON CONFLICT DO NOTHING;

COMMENT ON TABLE nibas_arbeidsliste_schema.sync_lock IS 'Single-row table for sync process coordination';
