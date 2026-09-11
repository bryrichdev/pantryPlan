-- An audit trail of every change to account data, for the admin Logs tab.
--
-- Triggers rather than application code, so nothing slips past: saves through
-- Hibernate, bulk deletes, rows removed by ON DELETE CASCADE, and changes made
-- by hand in psql are all recorded the same way.
--
-- The database cannot see who is signed in, so the application tells it. At
-- the start of each read-write transaction it sets two transaction-local
-- settings, pantryplan.actor and pantryplan.impersonator, to the signed-in
-- account's email and, when an admin is viewing as that account, the admin's
-- email. A change with no actor (registration, migrations, psql) is recorded
-- with an empty actor.
--
-- audit_log has no foreign keys on purpose. The record of a deleted account
-- has to outlive the account.

CREATE TABLE audit_log (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    table_name   VARCHAR(63)  NOT NULL,
    action       VARCHAR(6)   NOT NULL,
    row_id       BIGINT,
    actor        VARCHAR(255),
    impersonator VARCHAR(255),
    old_data     JSONB,
    new_data     JSONB,
    CONSTRAINT ck_audit_log_action CHECK (action IN ('INSERT', 'UPDATE', 'DELETE'))
);

CREATE INDEX ix_audit_log_table ON audit_log (table_name, id DESC);
CREATE INDEX ix_audit_log_actor ON audit_log (actor, id DESC);

CREATE FUNCTION audit_row_change() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    old_json JSONB;
    new_json JSONB;
BEGIN
    /* Password hashes never enter the log. */
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        old_json := to_jsonb(OLD) - 'password_hash';
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        new_json := to_jsonb(NEW) - 'password_hash';
    END IF;

    /* An update that only moved updated_at changed nothing worth reading and
       is skipped. The one exception is a password change: the hash itself is
       stripped above, so without this it would look like nothing happened.
       It is recorded as a "password changed" marker with no value. The hash is
       read through to_jsonb because OLD.password_hash would be an error on
       every other table. */
    IF TG_OP = 'UPDATE' AND (old_json - 'updated_at') = (new_json - 'updated_at') THEN
        IF (to_jsonb(OLD) ->> 'password_hash') IS DISTINCT FROM (to_jsonb(NEW) ->> 'password_hash') THEN
            new_json := new_json || '{"password": "changed"}';
            old_json := old_json || '{"password": "(hidden)"}';
        ELSE
            RETURN NULL;
        END IF;
    END IF;

    INSERT INTO audit_log (table_name, action, row_id, actor, impersonator, old_data, new_data)
    VALUES (
        TG_TABLE_NAME,
        TG_OP,
        (COALESCE(new_json, old_json) ->> 'id')::BIGINT,
        NULLIF(current_setting('pantryplan.actor', true), ''),
        NULLIF(current_setting('pantryplan.impersonator', true), ''),
        old_json,
        new_json
    );
    RETURN NULL;
END;
$$;

CREATE TRIGGER audit_users              AFTER INSERT OR UPDATE OR DELETE ON users              FOR EACH ROW EXECUTE FUNCTION audit_row_change();
CREATE TRIGGER audit_ingredients        AFTER INSERT OR UPDATE OR DELETE ON ingredients        FOR EACH ROW EXECUTE FUNCTION audit_row_change();
CREATE TRIGGER audit_recipes            AFTER INSERT OR UPDATE OR DELETE ON recipes            FOR EACH ROW EXECUTE FUNCTION audit_row_change();
CREATE TRIGGER audit_recipe_tags        AFTER INSERT OR UPDATE OR DELETE ON recipe_tags        FOR EACH ROW EXECUTE FUNCTION audit_row_change();
CREATE TRIGGER audit_recipe_lines       AFTER INSERT OR UPDATE OR DELETE ON recipe_lines       FOR EACH ROW EXECUTE FUNCTION audit_row_change();
CREATE TRIGGER audit_pantry_items       AFTER INSERT OR UPDATE OR DELETE ON pantry_items       FOR EACH ROW EXECUTE FUNCTION audit_row_change();
CREATE TRIGGER audit_meal_plans         AFTER INSERT OR UPDATE OR DELETE ON meal_plans         FOR EACH ROW EXECUTE FUNCTION audit_row_change();
CREATE TRIGGER audit_plan_entries       AFTER INSERT OR UPDATE OR DELETE ON plan_entries       FOR EACH ROW EXECUTE FUNCTION audit_row_change();
CREATE TRIGGER audit_grocery_lists      AFTER INSERT OR UPDATE OR DELETE ON grocery_lists      FOR EACH ROW EXECUTE FUNCTION audit_row_change();
CREATE TRIGGER audit_grocery_list_items AFTER INSERT OR UPDATE OR DELETE ON grocery_list_items FOR EACH ROW EXECUTE FUNCTION audit_row_change();
CREATE TRIGGER audit_cook_logs          AFTER INSERT OR UPDATE OR DELETE ON cook_logs          FOR EACH ROW EXECUTE FUNCTION audit_row_change();
