package fi.hel.resilient_logger.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

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

    private record SchemaRule(Object value, Predicate<Object> validator, String label) {
    }

    private static final Predicate<Object> nonEmptyList = it -> it instanceof List<?> list && !list.isEmpty();
    private static final Predicate<Object> nonEmptyString = it -> it instanceof String str && !str.isBlank();

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

        Map<String, SchemaRule> schema = Map.of(
                "sources", new SchemaRule(sources, nonEmptyList, "non-empty array"),
                "targets", new SchemaRule(targets, nonEmptyList, "non-empty array"),
                "origin", new SchemaRule(origin, nonEmptyString, "non-empty string"),
                "environment", new SchemaRule(environment, nonEmptyString, "non-empty string"));

        schema.forEach((key, rule) -> {
            if (!rule.validator().test(rule.value())) {
                throw new IllegalArgumentException(String.format(
                        "Configuration error: '%s' must be a %s.", key, rule.label()));
            }
        });
    }
    
    public static ResilientLoggerConfig fromConfig(Map<String, Object> config) {
        Map<String, Object> sanitizedConfig = new HashMap<>(config);
        sanitizedConfig.put("sources", Utils.ensureList(config.get("sources")));
        sanitizedConfig.put("targets", Utils.ensureList(config.get("targets")));

        return Utils.sharedObjectMapper().convertValue(sanitizedConfig, ResilientLoggerConfig.class);
    }
}