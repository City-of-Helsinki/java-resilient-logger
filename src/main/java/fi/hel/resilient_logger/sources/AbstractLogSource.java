package fi.hel.resilient_logger.sources;

import java.util.List;
import java.util.stream.Stream;

import fi.hel.resilient_logger.types.AuditLogDocument;
import fi.hel.resilient_logger.types.ComponentConfig;

public abstract class AbstractLogSource {
    protected final ComponentConfig config;

    /**
     * Requirement: Implementation must provide a public no-args constructor
     * for configuration-based instantiation.
     */
    protected AbstractLogSource(ComponentConfig config) {
        this.config = config;
    }

    /**
     * Queries and returns a stream of unsent log entries.
     */
    public abstract Stream<Entry> getUnsentEntries(int chunkSize);

    /**
     * Purges logs that have already been sent.
     */
    public abstract List<String> clearSentEntries(int daysToKeep);

    public interface Entry {
        /**
         * Unique identifier for this specific log entry (e.g., UUID or Database ID).
         */
        public abstract String getId();

        /**
         * Converts the log entry into a document format suitable for storage (e.g.,
         * Elasticsearch).
         */
        public abstract AuditLogDocument getDocument();

        /**
         * Returns true if this specific entry has already been successfully dispatched.
         */
        public abstract boolean isSent();

        /**
         * Marks this specific entry as sent in the underlying data store.
         */
        public abstract void markSent();
    }
}