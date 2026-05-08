package fi.hel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.hel.mocks.MockLogSource;
import fi.hel.mocks.MockLogTarget;
import fi.hel.resilient_logger.ResilientLogger;
import fi.hel.resilient_logger.sources.AbstractLogSource;
import fi.hel.resilient_logger.types.AuditLogEvent;
import fi.hel.resilient_logger.types.ComponentConfig;
import fi.hel.resilient_logger.types.ResilientLoggerConfig;

class ResilientLoggerMarkSentFailureTest {

    static class ThrowingMarkSentSource extends MockLogSource {
        ThrowingMarkSentSource(ComponentConfig config) {
            super(config);
        }

        @Override
        public void markSent(Collection<AbstractLogSource.Entry> entries) {
            throw new RuntimeException("simulated DB failure");
        }
    }

    @BeforeEach
    void setup() {
        MockLogSource.reset();
        MockLogTarget.reset();
    }

    @Test
    void markSentFailureFlipsAllResultsToFalse() {
        AuditLogEvent event = AuditLogEvent.builder()
                .operation("OP")
                .message("msg")
                .environment("test")
                .build();
        MockLogSource.addEntry("a", event);
        MockLogSource.addEntry("b", event);

        ThrowingMarkSentSource source = new ThrowingMarkSentSource(
                new ComponentConfig(Map.of("class", MockLogSource.class.getName())));
        MockLogTarget target = new MockLogTarget(
                new ComponentConfig(Map.of("class", MockLogTarget.class.getName())));

        ResilientLoggerConfig config = ResilientLoggerConfig.builder()
                .environment("test")
                .origin("test")
                .build();
        ResilientLogger logger = ResilientLogger.create(config, List.of(source), List.of(target));

        Map<String, Boolean> results = logger.submitUnsentEntries();

        assertFalse(results.get("a"), "markSent failure must flip already-shipped entries to false");
        assertFalse(results.get("b"), "markSent failure must flip already-shipped entries to false");
        assertTrue(MockLogTarget.submittedEntries().size() == 2,
                "entries were still submitted to the target before markSent failed");
    }
}
