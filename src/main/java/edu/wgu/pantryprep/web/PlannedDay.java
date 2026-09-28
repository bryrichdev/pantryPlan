package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.domain.PlanEntry;
import java.time.LocalDate;
import java.util.List;

/**
 * One day of a meal plan, ready for the template.
 *
 * <p>Built in the controller rather than filtered in the view, because picking
 * entries out of a flat list inside Thymeleaf is both awkward to read and easy
 * to get wrong. A plain class with getters rather than a record, since the
 * template's property access expects them.
 */
public class PlannedDay {

    private final LocalDate date;
    private final List<PlanEntry> entries;

    public PlannedDay(LocalDate date, List<PlanEntry> entries) {
        this.date = date;
        this.entries = entries;
    }

    public LocalDate getDate() {
        return date;
    }

    public List<PlanEntry> getEntries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int getEntryCount() {
        return entries.size();
    }
}
