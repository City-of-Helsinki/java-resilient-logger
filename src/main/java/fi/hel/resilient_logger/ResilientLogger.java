package fi.hel.resilient_logger;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import fi.hel.resilient_logger.sources.AbstractLogSource;
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
     */
    public static ResilientLogger create(ResilientLoggerConfig config) {
        try {
            List<AbstractLogSource> sources = new ArrayList<>();
            List<AbstractLogTarget> targets = new ArrayList<>();

            for (ComponentConfig source : config.sources()) {
                sources.add(Utils.instantiate(source.className(), AbstractLogSource.class, source));
            }
            
            for (ComponentConfig target : config.targets()) {
                targets.add(Utils.instantiate(target.className(), AbstractLogTarget.class, target));
            }

            return new ResilientLogger(config, sources, targets);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ResilientLogger from config", e);
        }
    }

    /**
     * Processes unsent entries. Uses try-with-resources to ensure the Stream (and
     * underlying resources)
     * are closed correctly.
     */
    public Map<String, Boolean> submitUnsentEntries() {
        Map<String, Boolean> results = new HashMap<>();

        try (Stream<AbstractLogSource.Entry> entries = logSources
                .stream()
                .flatMap(logSource -> logSource.getUnsentEntries(this.config.chunkSize()))) {
            entries
                    .limit(this.config.batchLimit())
                    .forEach(entry -> {
                        try {
                            boolean result = this.submit(entry);

                            if (result) {
                                entry.markSent();
                            }

                            results.put(entry.getId(), result);
                        } catch (Exception e) {
                            logger.log(Level.ERROR, "Critical failure processing entry {0}", entry.getId(), e);
                            results.put(entry.getId(), false);
                        }
                    });
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