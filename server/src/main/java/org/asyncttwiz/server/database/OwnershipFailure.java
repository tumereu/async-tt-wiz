package org.asyncttwiz.server.database;

/** Deliberately contains no JDBC exception cause, SQL, credentials or connection inputs. */
public final class OwnershipFailure extends RuntimeException {
    public enum Reason { DATABASE_UNAVAILABLE, UNSUPPORTED_SCHEMA, NOT_OWNER, TRANSACTION_FAILED, CLOSED }

    private final Reason reason;

    public OwnershipFailure(Reason reason) {
        super("Database ownership: " + reason.name(), null, false, true);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
