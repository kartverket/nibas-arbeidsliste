-- Create a table for distributed locking
CREATE TABLE IF NOT EXISTS database_lock (
    id INT PRIMARY KEY,
    locked_until TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Create an index for faster lookups
CREATE INDEX IF NOT EXISTS idx_database_lock_locked_until ON database_lock (locked_until);
