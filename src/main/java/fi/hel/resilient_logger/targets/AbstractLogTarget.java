package fi.hel.resilient_logger.targets;

import java.io.Closeable;

import fi.hel.resilient_logger.sources.AbstractLogSource;
import fi.hel.resilient_logger.types.ComponentConfig;

public abstract class AbstractLogTarget implements Closeable {
    protected final ComponentConfig config;
    protected final boolean required;

    /**
     * Reads the {@code required} flag from the supplied {@link ComponentConfig},
     * defaulting to {@code true} if the key is absent.
     *
     * <p>The {@code required} flag controls how a target's failure affects
     * the batch: if a required target's {@code submit()} returns false (or
     * throws), the entry is treated as not delivered and will be retried on
     * the next cycle, even if other targets succeeded. A non-required
     * target's failure is logged and ignored. The default is {@code true}
     * so that a misconfigured custom target fails closed (does not silently
     * drop entries).
     */
    public AbstractLogTarget(ComponentConfig config) {
        this(config, config.getValueOrDefault("required", true));
    }

    /**
     * Use this overload to set {@code required} explicitly when subclassing,
     * bypassing the {@code ComponentConfig} read.
     */
    public AbstractLogTarget(ComponentConfig config, boolean required) {
        this.config = config;
        this.required = required;
    }

    public boolean isRequired() {
        return this.required;
    }

    /**
     * Submits the log entry to the target.
     *
     * @param entry The log source to submit.
     * @return true if successful, false otherwise.
     */
    public abstract boolean submit(AbstractLogSource.Entry entry);

    /**
     * Releases any resources held by this target (e.g. HTTP clients,
     * connection pools). The default implementation does nothing.
     * Called by {@link fi.hel.resilient_logger.ResilientLogger#close()}.
     */
    @Override
    public void close() {
    }
}