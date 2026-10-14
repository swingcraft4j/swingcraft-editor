package com.swingcraft4j.code.diff;

/**
 * One place where two versions of a text differ: a run of lines of the original that the
 * revised version has replaced with a run of lines of its own. Either run may be empty, where
 * lines were only inserted or only deleted.
 *
 * @param originalStart the index of the first line of the run in the original, from 0; where
 *                      the run is empty, the index of the line it comes before
 * @param originalCount how many lines of the original were taken away
 * @param revisedStart  the index of the first line of the run in the revised version, from 0;
 *                      where the run is empty, the index of the line it comes before
 * @param revisedCount  how many lines the revised version put in their place
 */
public record Change(int originalStart, int originalCount, int revisedStart, int revisedCount) {

    public Change {
        if (originalStart < 0 || originalCount < 0 || revisedStart < 0 || revisedCount < 0) {
            throw new IllegalArgumentException("negative: " + originalStart + ", " + originalCount + ", "
                    + revisedStart + ", " + revisedCount);
        }
        if (originalCount == 0 && revisedCount == 0) {
            throw new IllegalArgumentException("a change of nothing");
        }
    }

    /** The index just past the run in the original. */
    public int originalEnd() {
        return originalStart + originalCount;
    }

    /** The index just past the run in the revised version. */
    public int revisedEnd() {
        return revisedStart + revisedCount;
    }

    /** Whether lines were only added: the original has none here. */
    public boolean isInsert() {
        return originalCount == 0;
    }

    /** Whether lines were only taken away: the revised version has none here. */
    public boolean isDelete() {
        return revisedCount == 0;
    }
}
