package edu.wgu.pantryplan.report;

import java.time.Instant;
import java.util.List;

/**
 * A timestamped, tabular account report. Concrete reports decide what their
 * rows mean while the web layer renders every report through the same view.
 */
public abstract class Report {

    private final String title;
    private final Instant generatedAt;
    private final List<String> columnHeaders;
    private List<ReportRow> rows = List.of();

    protected Report(String title, Instant generatedAt, List<String> columnHeaders) {
        this.title = title;
        this.generatedAt = generatedAt;
        this.columnHeaders = List.copyOf(columnHeaders);
    }

    /** Builds the report's rows from the account data supplied at construction. */
    public abstract void generate();

    protected final void setRows(List<ReportRow> rows) {
        this.rows = List.copyOf(rows);
    }

    public String getTitle() {
        return title;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public List<String> getColumnHeaders() {
        return columnHeaders;
    }

    public List<ReportRow> getRows() {
        return rows;
    }
}
