package fi.hel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.hel.mocks.MockLogSource;
import fi.hel.mocks.MockLogTarget;
import fi.hel.resilientlogger.ResilientLogger;
import fi.hel.resilientlogger.sources.AbstractLogSource;
import fi.hel.resilientlogger.types.AuditLogEvent;
import fi.hel.resilientlogger.types.ComponentConfig;
import fi.hel.resilientlogger.types.ResilientLoggerConfig;

class ResilientLoggerBatchMarkSentTest {

    /**
     * Records every batch passed to {@code markSent(Collection)} but does
     * not flip per-entry state. If the dispatch loop ever short-circuits
     * the batched hook by calling {@code entry.markSent()} directly, the
     * test will see {@code isSent() == true} on entries it never told to
     * be marked.
     */
    static class RecordingMockLogSource extends MockLogSource {
        final List<List<String>> batches = new ArrayList<>();

        RecordingMockLogSource(ComponentConfig config) {
            super(config);
        }

        @Override
        public void markSent(Collection<AbstractLogSource.Entry> entries) {
            batches.add(entries.stream().map(AbstractLogSource.Entry::getId).toList());
        }
    }

    @BeforeEach
    void setup() {
        MockLogSource.reset();
        MockLogTarget.reset();
    }

    @Test
    void batchMarkSentReceivesEverySuccessfulEntry() {
        AuditLogEvent event = AuditLogEvent.builder()
                .operation("OP")
                .message("msg")
                .environment("test")
                .build();
        List<AbstractLogSource.Entry> added = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            added.add(MockLogSource.addEntry("id-" + i, event));
        }

        RecordingMockLogSource source = new RecordingMockLogSource(
                new ComponentConfig(Map.of("class", MockLogSource.class.getName())));
        MockLogTarget target = new MockLogTarget(
                new ComponentConfig(Map.of("class", MockLogTarget.class.getName())));

        ResilientLoggerConfig config = ResilientLoggerConfig.builder()
                .environment("test")
                .origin("test")
                .build();

        try (ResilientLogger logger = ResilientLogger.create(config, List.of(source), List.of(target))) {
            logger.submitUnsentEntries();

            assertEquals(1, source.batches.size(), "markSent should be called exactly once for the chunk");
            assertEquals(List.of("id-0", "id-1", "id-2", "id-3", "id-4"), source.batches.get(0));
            assertTrue(added.stream().noneMatch(AbstractLogSource.Entry::isSent),
                    "dispatch loop must not short-circuit the batched hook by calling entry.markSent()");
        }
    }

    @Test
    void batchOnlyContainsSuccessfullyShippedEntries() {
        AuditLogEvent event = AuditLogEvent.builder()
                .operation("OP")
                .message("msg")
                .environment("test")
                .build();
        MockLogSource.addEntry("ok-id", event);

        RecordingMockLogSource source = new RecordingMockLogSource(
                new ComponentConfig(Map.of("class", MockLogSource.class.getName())));
        // Required target that fails → entry is NOT in the batch.
        MockLogTarget target = new MockLogTarget(
                new ComponentConfig(Map.of("class", MockLogTarget.class.getName(), "required", true)));
        MockLogTarget.setResult(false);

        ResilientLoggerConfig config = ResilientLoggerConfig.builder()
                .environment("test")
                .origin("test")
                .build();

        try (ResilientLogger logger = ResilientLogger.create(config, List.of(source), List.of(target))) {
            Map<String, Boolean> results = logger.submitUnsentEntries();

            assertFalse(results.get("ok-id"));
            assertTrue(source.batches.isEmpty(), "no entry shipped → markSent should not be called at all");
        }
    }
}
