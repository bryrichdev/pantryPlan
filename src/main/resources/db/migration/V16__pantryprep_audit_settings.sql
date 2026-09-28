-- Preserve V12 checksums on existing databases. Accept both application versions
-- so an image rollback continues to record audit attribution after this migration.
CREATE OR REPLACE FUNCTION audit_row_change() RETURNS TRIGGER
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
        COALESCE(NULLIF(current_setting('pantryprep.actor', true), ''), NULLIF(current_setting('pantryplan.actor', true), '')),
        COALESCE(NULLIF(current_setting('pantryprep.impersonator', true), ''), NULLIF(current_setting('pantryplan.impersonator', true), '')),
        old_json,
        new_json
    );
    RETURN NULL;
END;
$$;
