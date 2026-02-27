package fi.hel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.hel.mocks.MockLogSource;
import fi.hel.mocks.MockLogTarget;
import fi.hel.resilient_logger.ResilientLogger;
import fi.hel.resilient_logger.sources.AbstractLogSource.Entry;
import fi.hel.resilient_logger.types.AuditLogEvent;
import fi.hel.resilient_logger.types.ComponentConfig;
import fi.hel.resilient_logger.types.ResilientLoggerConfig;

class ResilientLoggerTest {
    ResilientLogger logger;

    @BeforeEach
    void setup() {
        MockLogSource.reset();
        MockLogTarget.reset();

        logger = ResilientLogger.create(
                ResilientLoggerConfig.builder()
                        .sources(List.of(
                            new ComponentConfig(MockLogSource.class.getName(), Map.of())
                        ))
                        .targets(List.of(
                            new ComponentConfig(MockLogTarget.class.getName(), Map.of())
                        ))
                        .environment("test")
                        .origin("test")
                        .build());
    }

    @Test
    void testSuccessfulEndToEndFlow() {
        MockLogSource.addEntry(
                "test-id-1",
                AuditLogEvent.builder()
                        .operation("TEST_OP")
                        .message("Unit Test Message")
                        .environment("test")
                        .build());

        MockLogTarget.setResult(true);

        Map<String, Boolean> results = logger.submitUnsentEntries();

        assertTrue(results.get("test-id-1"), "Entry should be marked as successful");
        assertEquals(1, results.size());
    }

    @Test
    void testRequiredTargetFailure() {
        MockLogSource.addEntry(
                "fail-id",
                AuditLogEvent.builder()
                        .operation("TEST_OP")
                        .message("Unit Test Message")
                        .environment("test")
                        .build());

        MockLogTarget.setResult(false);
        MockLogTarget.setRequired(true); // This target MUST succeed

        Map<String, Boolean> results = logger.submitUnsentEntries();

        // Verify that because the target failed and was required, the entry is FALSE
        assertFalse(results.get("fail-id"), "Required target failure should result in false");
    }

    @Test
    void testCleanupFlow() {
        Entry entry = MockLogSource.addEntry(
                "id-to-clean",
                AuditLogEvent.builder()
                        .operation("TEST_OP")
                        .message("Unit Test Message")
                        .environment("test")
                        .build());

        entry.markSent();
        List<String> clearedIds = logger.clearSentEntries();

        assertTrue(clearedIds.contains("id-to-clean"));
        assertTrue(logger.clearSentEntries().isEmpty(), "Source should be empty after first clear");
    }
}