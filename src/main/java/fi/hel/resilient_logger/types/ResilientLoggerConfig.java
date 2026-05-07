package fi.hel.resilient_logger.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import fi.hel.resilient_logger.builders.ResilientLoggerConfigBuilder;
import fi.hel.resilient_logger.utils.Utils;

public record ResilientLoggerConfig(
        List<ComponentConfig> sources,
        List<ComponentConfig> targets,
        String environment,
        String origin,
        @JsonProperty("batch_limit") int batchLimit,
        @JsonProperty("chunk_size") int chunkSize,
        @JsonProperty("store_old_entries_days") int storeOldEntriesDays) {

    private static final int DEFAULT_BATCH_LIMIT = 5000;
    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_STORE_OLD_ENTRIES_DAYS = 30;

    public static ResilientLoggerConfigBuilder builder() {
        return new ResilientLoggerConfigBuilder();
    }

    public ResilientLoggerConfig {
        batchLimit = batchLimit > 0 ? batchLimit : DEFAULT_BATCH_LIMIT;
        chunkSize = chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;
        storeOldEntriesDays = storeOldEntriesDays > 0 ? storeOldEntriesDays : DEFAULT_STORE_OLD_ENTRIES_DAYS;

        sources = sources != null ? sources : List.of();
        targets = targets != null ? targets : List.of();

        if (environment == null || environment.isBlank()) {
            throw new IllegalArgumentException(
                    "Configuration error: 'environment' must be a non-empty string.");
        }
        if (origin == null || origin.isBlank()) {
            throw new IllegalArgumentException(
                    "Configuration error: 'origin' must be a non-empty string.");
        }
    }

    public static ResilientLoggerConfig fromConfig(Map<String, Object> config) {
        Map<String, Object> sanitizedConfig = new HashMap<>(config);
        sanitizedConfig.put("sources", Utils.ensureList(config.getOrDefault("sources", List.of())));
        sanitizedConfig.put("targets", Utils.ensureList(config.getOrDefault("targets", List.of())));

        return Utils.sharedObjectMapper().convertValue(sanitizedConfig, ResilientLoggerConfig.class);
    }
}