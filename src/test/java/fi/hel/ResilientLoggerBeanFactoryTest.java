package fi.hel;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.hel.mocks.MockLogSource;
import fi.hel.mocks.MockLogTarget;
import fi.hel.resilient_logger.ResilientLogger;
import fi.hel.resilient_logger.types.AuditLogEvent;
import fi.hel.resilient_logger.types.ComponentConfig;
import fi.hel.resilient_logger.types.ResilientLoggerConfig;

class ResilientLoggerBeanFactoryTest {

    @BeforeEach
    void setup() {
        MockLogSource.reset();
        MockLogTarget.reset();
    }

    @Test
    void usesPreBuiltInstancesWithoutReflection() {
        ResilientLoggerConfig config = ResilientLoggerConfig.builder()
                .environment("test")
                .origin("test")
                .build();

        MockLogSource source = new MockLogSource(
                new ComponentConfig(Map.of("class", MockLogSource.class.getName())));
        MockLogTarget target = new MockLogTarget(
                new ComponentConfig(Map.of("class", MockLogTarget.class.getName())));

        MockLogSource.addEntry("bean-id", AuditLogEvent.builder()
                .operation("OP")
                .message("msg")
                .environment("test")
                .build());

        ResilientLogger logger = ResilientLogger.create(config, List.of(source), List.of(target));
        Map<String, Boolean> results = logger.submitUnsentEntries();

        assertTrue(results.get("bean-id"));
        assertTrue(MockLogTarget.submittedEntries().stream().anyMatch(e -> e.getId().equals("bean-id")),
                "the supplied target instance should have received the entry");
    }

    @Test
    void rejectsEmptySources() {
        ResilientLoggerConfig config = ResilientLoggerConfig.builder()
                .environment("test")
                .origin("test")
                .build();
        MockLogTarget target = new MockLogTarget(
                new ComponentConfig(Map.of("class", MockLogTarget.class.getName())));

        assertThrows(IllegalArgumentException.class,
                () -> ResilientLogger.create(config, List.of(), List.of(target)));
    }

    @Test
    void rejectsEmptyTargets() {
        ResilientLoggerConfig config = ResilientLoggerConfig.builder()
                .environment("test")
                .origin("test")
                .build();
        MockLogSource source = new MockLogSource(
                new ComponentConfig(Map.of("class", MockLogSource.class.getName())));

        assertThrows(IllegalArgumentException.class,
                () -> ResilientLogger.create(config, List.of(source), List.of()));
    }
}
