package edu.wgu.pantryprep.audit;

import java.util.List;

/**
 * One page of log entries, where it sits among all pages, and the filters that
 * produced it. Page numbers are zero-based here and shown one-based in the view.
 */
public class AuditLogPage {

    private final List<AuditLogEntry> entries;
    private final int page;
    private final int size;
    private final long totalEntries;
    private final int totalPages;
    private final List<Integer> pageNumbers;
    private final String table;
    private final String action;
    private final String actor;

    public AuditLogPage(List<AuditLogEntry> entries, int page, int size, long totalEntries, int totalPages,
                        List<Integer> pageNumbers, String table, String action, String actor) {
        this.entries = List.copyOf(entries);
        this.page = page;
        this.size = size;
        this.totalEntries = totalEntries;
        this.totalPages = totalPages;
        this.pageNumbers = List.copyOf(pageNumbers);
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

    public int getSize() {
        return size;
    }

    public long getTotalEntries() {
        return totalEntries;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public List<Integer> getPageNumbers() {
        return pageNumbers;
    }

    public boolean isHasNewer() {
        return page > 0;
    }

    public boolean isHasOlder() {
        return page < totalPages - 1;
    }

    /** One-based position of the first entry shown, for "Showing 51–100 of 240". */
    public long getFirstShown() {
        return entries.isEmpty() ? 0 : (long) page * size + 1;
    }

    public long getLastShown() {
        return (long) page * size + entries.size();
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
