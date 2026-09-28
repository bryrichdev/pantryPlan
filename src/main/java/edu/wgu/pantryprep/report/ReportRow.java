package edu.wgu.pantryprep.report;

import java.util.List;

/** One display-ready row in a tabular report. */
public class ReportRow {

    private final List<String> values;

    public ReportRow(List<String> values) {
        this.values = List.copyOf(values);
    }

    public List<String> getValues() {
        return values;
    }
}
