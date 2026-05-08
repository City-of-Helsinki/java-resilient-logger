package fi.hel.resilientlogger.sources;

import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import fi.hel.resilientlogger.types.AuditLogDocument;
import fi.hel.resilientlogger.types.ComponentConfig;

/**
 * Base type for log sources. The {@link Entry} contract defined here is
 * normative for both reflection-driven and bean-driven uses (see
 * {@link fi.hel.resilientlogger.ResilientLogger#create(fi.hel.resilientlogger.types.ResilientLoggerConfig)}
 * and the pre-built-instances overload).
 */
public abstract class AbstractLogSource implements Closeable {
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

    /**
     * Releases any resources held by this source (e.g. database
     * connections, executors). The default implementation does nothing.
     * Called by {@link fi.hel.resilientlogger.ResilientLogger#close()}.
     */
    @Override
    public void close() {
    }

    public interface Entry {
        /**
         * Unique identifier for this specific log entry (e.g., UUID or Database ID).
         */
        String getId();

        /**
         * Converts the log entry into a document format suitable for storage
         * (e.g., Elasticsearch).
         *
         * <p><b>Must be deterministic for the lifetime of the entry.</b>
         * {@code ElasticsearchLogTarget} uses a content hash of the
         * serialized document as the document ID with {@code OpType.Create},
         * so any difference between calls (e.g., a fresh
         * {@code OffsetDateTime.now()} baked in at call time, or a mutable
         * field touched between attempts) produces a different ID and
         * causes a duplicate document on retry. Capture timestamps at
         * entity-creation time, not at {@code getDocument()} time.
         */
        AuditLogDocument getDocument();

        /**
         * Returns true if this specific entry has already been successfully dispatched.
         */
        boolean isSent();

        /**
         * Marks this specific entry as sent in the underlying data store.
         *
         * <p>Should be effectively idempotent: calling {@code markSent()} on
         * an already-sent entry must be safe. Throwing from this method
         * causes the entry to be retried on the next cycle, so the
         * implementer is responsible for transactional behavior. The
         * batched {@link AbstractLogSource#markSent(Collection)} hook is
         * preferred for most data stores.
         */
        void markSent();
    }
}