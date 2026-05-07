package fi.hel.resilient_logger;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import fi.hel.resilient_logger.sources.AbstractLogSource;
import fi.hel.resilient_logger.sources.AbstractLogSource.Entry;
import fi.hel.resilient_logger.targets.AbstractLogTarget;
import fi.hel.resilient_logger.types.ComponentConfig;
import fi.hel.resilient_logger.types.ResilientLoggerConfig;
import fi.hel.resilient_logger.utils.Utils;

public class ResilientLogger {
    private static final Logger logger = System.getLogger(ResilientLogger.class.getName());

    private final ResilientLoggerConfig config;
    private final List<AbstractLogSource> logSources;
    private final List<AbstractLogTarget> logTargets;

    private ResilientLogger(
            ResilientLoggerConfig config,
            List<AbstractLogSource> logSources,
            List<AbstractLogTarget> logTargets) {
        this.config = config;
        this.logSources = logSources;
        this.logTargets = logTargets;
    }

    /**
     * Factory method to initialize the logger with class names from configuration.
     * Sources and targets are instantiated by reflection from the
     * {@code className} on each {@link ComponentConfig}.
     */
    public static ResilientLogger create(ResilientLoggerConfig config) {
        if (config.sources().isEmpty()) {
            throw new IllegalArgumentException(
                    "Configuration error: 'sources' must be a non-empty array.");
        }
        if (config.targets().isEmpty()) {
            throw new IllegalArgumentException(
                    "Configuration error: 'targets' must be a non-empty array.");
        }

        try {
            List<AbstractLogSource> sources = new ArrayList<>();
            List<AbstractLogTarget> targets = new ArrayList<>();

            for (ComponentConfig source : config.sources()) {
                sources.add(Utils.instantiate(source.className(), AbstractLogSource.class, source));
            }

            for (ComponentConfig target : config.targets()) {
                targets.add(Utils.instantiate(target.className(), AbstractLogTarget.class, target));
            }

            return create(config, sources, targets);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ResilientLogger from config", e);
        }
    }

    /**
     * Factory method to initialize the logger with pre-built sources and
     * targets. Use this overload when sources/targets are wired by an
     * external container (e.g. Spring) and need to receive constructor-
     * injected dependencies that the reflection path cannot supply.
     */
    public static ResilientLogger create(
            ResilientLoggerConfig config,
            List<AbstractLogSource> sources,
            List<AbstractLogTarget> targets) {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("At least one log source is required.");
        }
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("At least one log target is required.");
        }
        return new ResilientLogger(config, List.copyOf(sources), List.copyOf(targets));
    }

    /**
     * Processes unsent entries. Sources are iterated sequentially so each
     * source can be told which of its entries actually shipped, and the
     * configured {@code batchLimit} is preserved across them.
     */
    public Map<String, Boolean> submitUnsentEntries() {
        Map<String, Boolean> results = new HashMap<>();
        int remaining = config.batchLimit();

        for (AbstractLogSource source : logSources) {
            if (remaining <= 0) {
                break;
            }

            List<Entry> sentInChunk = new ArrayList<>();
            AtomicInteger processed = new AtomicInteger();

            try (Stream<Entry> entries = source.getUnsentEntries(config.chunkSize())) {
                entries.limit(remaining).forEach(entry -> {
                    processed.incrementAndGet();
                    boolean ok;
                    try {
                        ok = submit(entry);
                    } catch (Exception e) {
                        logger.log(Level.ERROR, "Critical failure processing entry {0}", entry.getId(), e);
                        ok = false;
                    }
                    results.put(entry.getId(), ok);
                    if (ok) {
                        sentInChunk.add(entry);
                    }
                });
            }

            if (!sentInChunk.isEmpty()) {
                try {
                    source.markSent(sentInChunk);
                } catch (Exception e) {
                    logger.log(Level.ERROR, "Failed to mark {0} entries as sent on source {1}",
                            sentInChunk.size(), source.getClass().getName(), e);
                    sentInChunk.forEach(entry -> results.put(entry.getId(), false));
                }
            }

            remaining -= processed.get();
        }

        return results;
    }

    /**
     * Cleans up sent entries across all sources.
     */
    public List<String> clearSentEntries() {
        try (Stream<String> ids = logSources
                .stream()
                .flatMap(logSource -> logSource.clearSentEntries(this.config.storeOldEntriesDays()).stream())) {
            return ids.toList();
        } catch (Exception e) {
            logger.log(Level.ERROR, "Failed to clear sent entries across sources", e);
            return List.of();
        }
    }

    /**
     * Submits an entry to all targets.
     * If a target is 'required' and fails, the whole submission is considered
     * failed.
     */
    private boolean submit(AbstractLogSource.Entry entry) {
        for (AbstractLogTarget target : logTargets) {
            try {
                boolean submitted = target.submit(entry);

                if (!submitted && target.isRequired()) {
                    return false;
                }
            } catch (Exception e) {
                logger.log(Level.ERROR, "Target {0} threw exception during submission", target.getClass().getName(), e);

                if (target.isRequired()) {
                    return false;
                }
            }
        }
        return true;
    }
}