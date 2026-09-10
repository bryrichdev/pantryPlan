package edu.wgu.pantryplan.service;

import java.util.ArrayList;
import java.util.List;

/**
 * What happened during a bulk delete.
 *
 * <p>A batch is not all or nothing. Some records refuse to be deleted because
 * other rows still point at them, and reporting "deleted 4, kept 2 because they
 * are still in use" is more honest — and more useful — than either rolling the
 * whole batch back or dropping the refusals silently.
 */
public class BulkDeleteResult {

    private int deletedCount;
    private int missingCount;
    private final List<String> blockedNames = new ArrayList<>();

    void recordDeleted() {
        deletedCount++;
    }

    /** A selected id that no longer exists, or never belonged to this account. */
    void recordMissing() {
        missingCount++;
    }

    void recordBlocked(String name) {
        blockedNames.add(name);
    }

    public int getDeletedCount() {
        return deletedCount;
    }

    public int getMissingCount() {
        return missingCount;
    }

    public List<String> getBlockedNames() {
        return List.copyOf(blockedNames);
    }

    public boolean hasBlocked() {
        return !blockedNames.isEmpty();
    }

    public boolean deletedNothing() {
        return deletedCount == 0;
    }

    /**
     * A sentence naming what was kept back, for showing to the cook.
     */
    public String describeBlocked() {
        if (blockedNames.isEmpty()) {
            return "";
        }
        String names = String.join(", ", blockedNames);
        if (blockedNames.size() == 1) {
            return names + " was kept because it is still in use.";
        }
        return blockedNames.size() + " were kept because they are still in use: " + names + ".";
    }
}
