package org.asyncttwiz.server.database;

/**
 * One stored row (a game's play, a pod's draft, a preset...) that the current code can no longer
 * decode, for example after an incompatible format change. Only requests for that row fail with it;
 * lists and background work leave the row out. Nothing is deleted or rewritten. Carries only the
 * row's kind and id, never the stored content or the decoding cause, so it may cross the
 * transaction boundary like {@link OwnershipFailure}.
 */
public final class UnreadableStoredData extends RuntimeException {
    private final String kind;
    private final String id;

    public UnreadableStoredData(String kind, String id) {
        super("Unreadable stored " + kind + " " + id, null, false, true);
        this.kind = kind;
        this.id = id;
    }

    public String kind() { return kind; }
    public String id() { return id; }
}
