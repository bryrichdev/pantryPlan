package edu.wgu.pantryplan.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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

    /** The page sizes the admin can choose from. Anything else falls back to the default. */
    public static final List<Integer> PAGE_SIZES = List.of(25, 50, 100, 200);

    public static final int DEFAULT_PAGE_SIZE = 50;

    /** How many numbered page links the pager shows at once. */
    private static final int PAGE_LINKS = 5;

    /* Shared by the page query and the count, so the total always matches what is listed. */
    private static final String FILTERS = """
            WHERE (:table = '' OR a.table_name = :table)
              AND (:action = '' OR a.action = :action)
              AND (:actor = '' OR a.actor ILIKE '%' || :actor || '%' OR a.impersonator ILIKE '%' || :actor || '%')
            """;

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
            """ + FILTERS + """
            ORDER BY a.id DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT = "SELECT COUNT(*) FROM audit_log a " + FILTERS;

    private final NamedParameterJdbcTemplate jdbc;

    public AuditLogService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One page of the log, newest first. Unknown filter values are ignored
     * rather than matching nothing, so a hand-edited URL still shows the log.
     * A page past the end shows the last page instead of an empty one.
     *
     * @param page zero-based
     * @param size one of {@link #PAGE_SIZES}
     */
    public AuditLogPage search(String table, String action, String actor, int page, int size) {
        String tableFilter = table != null && TABLES.containsKey(table) ? table : "";
        String actionFilter = action != null && ACTIONS.containsKey(action) ? action : "";
        String actorFilter = actor == null ? "" : actor.trim();
        int pageSize = PAGE_SIZES.contains(size) ? size : DEFAULT_PAGE_SIZE;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("table", tableFilter)
                .addValue("action", actionFilter)
                .addValue("actor", actorFilter);

        Long counted = jdbc.queryForObject(COUNT, params, Long.class);
        long total = counted == null ? 0 : counted;
        int totalPages = (int) Math.max(1, (total + pageSize - 1) / pageSize);
        int current = Math.min(Math.max(page, 0), totalPages - 1);

        params.addValue("limit", pageSize).addValue("offset", (long) current * pageSize);
        List<AuditLogEntry> entries = jdbc.query(QUERY, params, AuditLogService::toEntry);

        return new AuditLogPage(entries, current, pageSize, total, totalPages, pageWindow(current, totalPages),
                tableFilter, actionFilter, actorFilter);
    }

    /** The same search at the default page size. */
    public AuditLogPage search(String table, String action, String actor, int page) {
        return search(table, action, actor, page, DEFAULT_PAGE_SIZE);
    }

    /**
     * Empties the log and returns how many rows went.
     *
     * <p>DELETE rather than TRUNCATE. It reports its own row count, which the
     * admin sees, and it leaves the identity sequence where it is, so a cleared
     * id is never handed out twice.
     *
     * <p>audit_log carries no trigger of its own, so this leaves nothing behind
     * in the log. The clear is written to the application log instead.
     */
    @Transactional
    public int clear() {
        return jdbc.getJdbcTemplate().update("DELETE FROM audit_log");
    }

    /**
     * Up to five page numbers, keeping the current page in the middle where it
     * can. Near either end the window slides over so it still shows five:
     * pages 1 to 5 while on page 2, and the last five while on the last page.
     */
    public static List<Integer> pageWindow(int current, int totalPages) {
        int start = Math.max(0, current - PAGE_LINKS / 2);
        int end = Math.min(totalPages - 1, start + PAGE_LINKS - 1);
        start = Math.max(0, end - PAGE_LINKS + 1);
        List<Integer> pages = new java.util.ArrayList<>();
        for (int i = start; i <= end; i++) {
            pages.add(i);
        }
        return pages;
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
