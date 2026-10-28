-- C G07/G15/G16 metadata added to D's persistent guide schema.
-- Incremental only: never execute the standalone DROP/CREATE schema.sql.
ALTER TABLE relation_definition ADD COLUMN direction_rule VARCHAR(2000);
ALTER TABLE relation_definition ADD COLUMN min_participants INT;
ALTER TABLE relation_definition ADD COLUMN max_participants INT;
ALTER TABLE relation_definition ADD COLUMN sort_order INT;
