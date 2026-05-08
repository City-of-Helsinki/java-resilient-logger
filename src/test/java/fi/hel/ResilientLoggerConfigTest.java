package fi.hel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import fi.hel.resilientlogger.ResilientLogger;
import fi.hel.resilientlogger.types.ResilientLoggerConfig;

public class ResilientLoggerConfigTest {
    @Test
    void shouldHandleMapOfMaps() {
        // Simulate the exact Map structure Spring produced
        Map<String, Object> sources = Map.of(
                "0", Map.of("class", "fi.hel.SourceA"));

        Map<String, Object> targets = Map.of(
                "0", Map.of("class", "fi.hel.TargetA"));

        Map<String, Object> rawConfig = Map.of(
                "environment", "test",
                "origin", "test-service",
                "sources", sources,
                "targets", targets
        );

        ResilientLoggerConfig config = ResilientLoggerConfig.fromConfig(rawConfig);
        assertEquals(1, config.sources().size());
        assertEquals("fi.hel.SourceA", config.sources().get(0).getValue("class", String.class));
        assertEquals("fi.hel.TargetA", config.targets().get(0).getValue("class", String.class));
    }

    @Test
    void shouldHandleListOfMaps() {
        List<Object> sources = List.of(
                Map.of("class", "fi.hel.SourceA"));

        List<Object> targets = List.of(
                Map.of("class", "fi.hel.TargetA"));

        Map<String, Object> rawConfig = Map.of(
                "environment", "test",
                "origin", "test-service",
                "sources", sources,
                "targets", targets
        );

        ResilientLoggerConfig config = ResilientLoggerConfig.fromConfig(rawConfig);
        assertEquals(1, config.sources().size());
        assertEquals("fi.hel.SourceA", config.sources().get(0).getValue("class", String.class));
        assertEquals("fi.hel.TargetA", config.targets().get(0).getValue("class", String.class));
    }

    @Test
    void recordAcceptsEmptySourcesAndTargetsForBeanPath() {
        ResilientLoggerConfig config = ResilientLoggerConfig.builder()
                .environment("test")
                .origin("test")
                .build();

        assertTrue(config.sources().isEmpty());
        assertTrue(config.targets().isEmpty());
    }

    @Test
    void reflectionFactoryRejectsEmptySources() {
        ResilientLoggerConfig config = ResilientLoggerConfig.builder()
                .environment("test")
                .origin("test")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ResilientLogger.create(config));
        assertTrue(ex.getMessage().contains("sources"));
    }

    @Test
    void fromConfigDefaultsMissingSourcesAndTargetsToEmpty() {
        Map<String, Object> rawConfig = Map.of(
                "environment", "test",
                "origin", "test-service");

        ResilientLoggerConfig config = ResilientLoggerConfig.fromConfig(rawConfig);
        assertTrue(config.sources().isEmpty());
        assertTrue(config.targets().isEmpty());
    }
}
