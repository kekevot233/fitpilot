ALTER TABLE foods
    ADD COLUMN owner_id bigint,
    ADD COLUMN active boolean,
    ADD CONSTRAINT fk_foods_owner FOREIGN KEY (owner_id) REFERENCES users (id);

ALTER TABLE foods DROP CONSTRAINT uk_foods_name;

CREATE UNIQUE INDEX uk_foods_builtin_name_lower
    ON foods (lower(name))
    WHERE owner_id IS NULL;

CREATE UNIQUE INDEX uk_foods_owner_name_lower
    ON foods (owner_id, lower(name))
    WHERE owner_id IS NOT NULL AND (active IS NULL OR active = true);
