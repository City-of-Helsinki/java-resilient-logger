package fi.hel.resilient_logger.targets;

import java.io.Closeable;

import fi.hel.resilient_logger.sources.AbstractLogSource;
import fi.hel.resilient_logger.types.ComponentConfig;

public abstract class AbstractLogTarget implements Closeable {
    protected final ComponentConfig config;
    protected final boolean required;

    public AbstractLogTarget(ComponentConfig config) {
        this(config, config.getValueOrDefault("required", true));
    }

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