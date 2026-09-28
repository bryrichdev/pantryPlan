package edu.wgu.pantryprep.service;

/**
 * Outcome of marking one or more planned meals as cooked.
 *
 * <p>Cooked entries are intentionally skipped on a repeat request. This keeps
 * a double-click or a stale bulk selection from deducting pantry stock twice.
 */
public class CookResult {

    private int cookedCount;
    private int alreadyCookedCount;
    private int missingCount;
    private int unconvertibleLineCount;

    void recordCooked() {
        cookedCount++;
    }

    void recordAlreadyCooked() {
        alreadyCookedCount++;
    }

    void recordMissing() {
        missingCount++;
    }

    void recordUnconvertibleLine() {
        unconvertibleLineCount++;
    }

    public int getCookedCount() {
        return cookedCount;
    }

    public int getAlreadyCookedCount() {
        return alreadyCookedCount;
    }

    public int getMissingCount() {
        return missingCount;
    }

    public int getUnconvertibleLineCount() {
        return unconvertibleLineCount;
    }
}
