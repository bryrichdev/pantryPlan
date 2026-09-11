package edu.wgu.pantryplan.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Reads the audit log for the admin Logs tab.
 *
 * <p>Plain SQL through JdbcTemplate rather than a JPA entity. The log is
 * written only by database triggers and never changed by the application, and
 * the readable summary of each change is built from its JSON in the query
 * itself.
 */
@Service
public class AuditLogService {

    public static final int PAGE_SIZE = 50;

    /** Audited tables and their display names, in the order the filter lists them. */
    public static final Map<String, String> TABLES = orderedMap(
            "users", "Accounts",
            "ingredients", "Ingredients",
            "recipes", "Recipes",
            "recipe_lines", "Recipe lines",
            "recipe_tags", "Recipe tags",
            "pantry_items", "Pantry items",
            "meal_plans", "Meal plans",
            "plan_entries", "Planned meals",
            "grocery_lists", "Grocery lists",
            "grocery_list_items", "Grocery list items",
            "cook_logs", "Cooking history");

    public static final Map<String, String> ACTIONS = orderedMap(
            "INSERT", "Created",
            "UPDATE", "Changed",
            "DELETE", "Deleted");

    /*
     * For a change, the summary lists only the fields that differ, as
     * "field: old → new", ignoring updated_at. For a create or delete it shows
     * whatever best names the row: its name, title, email, or tag, and failing
     * those its amount or its date and meal. Long values are cut short.
     */
    private static final String QUERY = """
            SELECT a.id, a.occurred_at, a.table_name, a.action, a.row_id, a.actor, a.impersonator,
                   CASE a.action
                     WHEN 'UPDATE' THEN (
                         SELECT string_agg(n.key || ': '
                                    || LEFT(COALESCE(a.old_data ->> n.key, '(blank)'), 60) || ' → '
                                    || LEFT(COALESCE(n.value #>> '{}', '(blank)'), 60), '; ' ORDER BY n.key)
                         FROM jsonb_each(a.new_data) n
                         WHERE n.key <> 'updated_at' AND (a.old_data -> n.key) IS DISTINCT FROM n.value)
                     ELSE LEFT(COALESCE(
                         d.data ->> 'name', d.data ->> 'title', d.data ->> 'email', d.data ->> 'tag',
                         NULLIF(concat_ws(' ', d.data ->> 'quantity', d.data ->> 'needed_quantity',
                                               d.data ->> 'quantity_deducted', d.data ->> 'unit'), ''),
                         concat_ws(' ', d.data ->> 'plan_date', d.data ->> 'meal_slot')), 120)
                   END AS summary
            FROM audit_log a
            CROSS JOIN LATERAL (SELECT COALESCE(a.new_data, a.old_data) AS data) d
            WHERE (:table = '' OR a.table_name = :table)
              AND (:action = '' OR a.action = :action)
              AND (:actor = '' OR a.actor ILIKE '%' || :actor || '%' OR a.impersonator ILIKE '%' || :actor || '%')
            ORDER BY a.id DESC
            LIMIT :limit OFFSET :offset
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AuditLogService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One page of the log, newest first. Unknown filter values are ignored
     * rather than matching nothing, so a hand-edited URL still shows the log.
     *
     * @param page zero-based
     */
    public AuditLogPage search(String table, String action, String actor, int page) {
        String tableFilter = table != null && TABLES.containsKey(table) ? table : "";
        String actionFilter = action != null && ACTIONS.containsKey(action) ? action : "";
        String actorFilter = actor == null ? "" : actor.trim();
        int safePage = Math.max(page, 0);

        /* One extra row is fetched only to learn whether an older page exists. */
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("table", tableFilter)
                .addValue("action", actionFilter)
                .addValue("actor", actorFilter)
                .addValue("limit", PAGE_SIZE + 1)
                .addValue("offset", safePage * PAGE_SIZE);
        List<AuditLogEntry> rows = jdbc.query(QUERY, params, AuditLogService::toEntry);

        boolean hasOlder = rows.size() > PAGE_SIZE;
        List<AuditLogEntry> entries = hasOlder ? rows.subList(0, PAGE_SIZE) : rows;
        return new AuditLogPage(entries, safePage, hasOlder, tableFilter, actionFilter, actorFilter);
    }

    private static AuditLogEntry toEntry(ResultSet rs, int rowNum) throws SQLException {
        return new AuditLogEntry(
                rs.getLong("id"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("table_name"),
                rs.getString("action"),
                rs.getObject("row_id", Long.class),
                rs.getString("actor"),
                rs.getString("impersonator"),
                rs.getString("summary"));
    }

    private static Map<String, String> orderedMap(String... pairs) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(map);
    }
}
