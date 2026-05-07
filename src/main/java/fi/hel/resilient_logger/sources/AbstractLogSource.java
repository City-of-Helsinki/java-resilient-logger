package fi.hel.resilient_logger.sources;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import fi.hel.resilient_logger.types.AuditLogDocument;
import fi.hel.resilient_logger.types.ComponentConfig;

public abstract class AbstractLogSource {
    protected final ComponentConfig config;

    /**
     * Construction paths:
     * <ul>
     *   <li>When loaded via {@code ResilientLogger.create(config)} (the
     *       reflection path), implementations must expose a public
     *       constructor accepting a single {@link ComponentConfig} argument.</li>
     *   <li>When registered as a pre-built instance via the
     *       {@code ResilientLogger.create(config, sources, targets)} overload,
     *       implementations may use any constructor signature they like
     *       (including ones with framework-injected dependencies).</li>
     * </ul>
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

    /**
     * Marks a batch of entries as sent. The default implementation calls
     * {@link Entry#markSent()} on each entry, which preserves the per-row
     * behavior expected by existing implementations. JPA-backed sources
     * should override this with a single bulk update
     * (e.g. {@code UPDATE ... WHERE id IN (...)}) to avoid one round-trip
     * per entry.
     */
    public void markSent(Collection<Entry> entries) {
        entries.forEach(Entry::markSent);
    }

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