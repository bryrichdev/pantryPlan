package edu.wgu.pantryprep.service;

/** Outcome of reversing one or more cooked meal entries. */
public class CookUndoResult {

    private int uncookedCount;
    private int alreadyUncookedCount;
    private int missingCount;
    private int unrestorableLogCount;

    void recordUncooked() {
        uncookedCount++;
    }

    void recordAlreadyUncooked() {
        alreadyUncookedCount++;
    }

    void recordMissing() {
        missingCount++;
    }

    void recordUnrestorableLog() {
        unrestorableLogCount++;
    }

    public int getUncookedCount() {
        return uncookedCount;
    }

    public int getAlreadyUncookedCount() {
        return alreadyUncookedCount;
    }

    public int getMissingCount() {
        return missingCount;
    }

    public int getUnrestorableLogCount() {
        return unrestorableLogCount;
    }
}
