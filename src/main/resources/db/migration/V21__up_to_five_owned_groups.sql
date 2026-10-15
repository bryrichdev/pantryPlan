-- An account can now create up to five groups, rather than one. Joining is
-- unchanged.
--
-- The two indexes that held owners to a single group go. A trigger takes
-- their place and allows up to five groups per owner, counting a group handed
-- over by transfer the same as one created. The owner's account row is locked
-- first, so two requests at once cannot both slip in as the fifth.
--
-- The service checks the same limit first and gives a friendly message; this
-- is the backstop for any path that gets past it.

ALTER TABLE kitchen_groups DROP CONSTRAINT uq_kitchen_groups_owner;
DROP INDEX uq_group_members_one_group_per_owner;

-- Owner lookups no longer have the unique constraint's index to use.
CREATE INDEX ix_kitchen_groups_owner ON kitchen_groups (owner_id);

CREATE FUNCTION kitchen_groups_owner_limit() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.owner_id = OLD.owner_id THEN
        RETURN NEW;
    END IF;

    PERFORM 1 FROM users WHERE id = NEW.owner_id FOR UPDATE;

    IF (SELECT COUNT(*) FROM kitchen_groups
        WHERE owner_id = NEW.owner_id AND id <> NEW.id) >= 5 THEN
        RAISE EXCEPTION 'user % already owns five groups', NEW.owner_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER kitchen_groups_owner_limit
    BEFORE INSERT OR UPDATE OF owner_id ON kitchen_groups
    FOR EACH ROW EXECUTE FUNCTION kitchen_groups_owner_limit();
