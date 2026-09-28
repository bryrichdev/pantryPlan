package edu.wgu.pantryprep.audit;

import java.time.Instant;

/** One recorded change, ready to display. A plain class so Thymeleaf can read it. */
public class AuditLogEntry {

    private final long id;
    private final Instant occurredAt;
    private final String tableName;
    private final String action;
    private final Long rowId;
    private final String actor;
    private final String impersonator;
    private final String summary;

    public AuditLogEntry(long id, Instant occurredAt, String tableName, String action, Long rowId,
                         String actor, String impersonator, String summary) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.tableName = tableName;
        this.action = action;
        this.rowId = rowId;
        this.actor = actor;
        this.impersonator = impersonator;
        this.summary = summary;
    }

    public long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getTableName() {
        return tableName;
    }

    public String getTableLabel() {
        return AuditLogService.TABLES.getOrDefault(tableName, tableName);
    }

    public String getAction() {
        return action;
    }

    public String getActionLabel() {
        return AuditLogService.ACTIONS.getOrDefault(action, action);
    }

    /** Colour carries meaning: green for created, amber for changed, rust for deleted. */
    public String getActionPill() {
        return switch (action) {
            case "INSERT" -> "pill--good";
            case "DELETE" -> "pill--bad";
            default -> "pill--warn";
        };
    }

    public Long getRowId() {
        return rowId;
    }

    public String getActor() {
        return actor;
    }

    public String getImpersonator() {
        return impersonator;
    }

    public String getSummary() {
        return summary;
    }
}
