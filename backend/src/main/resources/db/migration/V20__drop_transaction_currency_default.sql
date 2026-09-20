-- The default in V19 existed only to backfill pre-existing rows (all of which are genuinely USD -
-- no other currency has ever been used). Dropping it means every future insert must specify a
-- currency explicitly; the application always does, and an insert that forgets to now fails
-- loudly instead of silently defaulting.
ALTER TABLE transaction ALTER COLUMN currency DROP DEFAULT;
