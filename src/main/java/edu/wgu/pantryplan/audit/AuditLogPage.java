package edu.wgu.pantryplan.audit;

import java.util.List;

/** One page of log entries plus the filters that produced it, for the view. */
public class AuditLogPage {

    private final List<AuditLogEntry> entries;
    private final int page;
    private final boolean hasOlder;
    private final String table;
    private final String action;
    private final String actor;

    public AuditLogPage(List<AuditLogEntry> entries, int page, boolean hasOlder,
                        String table, String action, String actor) {
        this.entries = List.copyOf(entries);
        this.page = page;
        this.hasOlder = hasOlder;
        this.table = table;
        this.action = action;
        this.actor = actor;
    }

    public List<AuditLogEntry> getEntries() {
        return entries;
    }

    public int getPage() {
        return page;
    }

    public boolean isHasNewer() {
        return page > 0;
    }

    public boolean isHasOlder() {
        return hasOlder;
    }

    public String getTable() {
        return table;
    }

    public String getAction() {
        return action;
    }

    public String getActor() {
        return actor;
    }

    public boolean isFiltered() {
        return !table.isEmpty() || !action.isEmpty() || !actor.isEmpty();
    }
}
