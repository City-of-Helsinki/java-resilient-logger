package fi.hel.resilient_logger.targets;

import fi.hel.resilient_logger.sources.AbstractLogSource;
import fi.hel.resilient_logger.types.ComponentConfig;

public abstract class AbstractLogTarget {
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
}