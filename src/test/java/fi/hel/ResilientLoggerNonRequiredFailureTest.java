package fi.hel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.hel.mocks.MockLogSource;
import fi.hel.mocks.MockLogTarget;
import fi.hel.resilientlogger.ResilientLogger;
import fi.hel.resilientlogger.sources.AbstractLogSource;
import fi.hel.resilientlogger.targets.AbstractLogTarget;
import fi.hel.resilientlogger.types.AuditLogEvent;
import fi.hel.resilientlogger.types.ComponentConfig;
import fi.hel.resilientlogger.types.ResilientLoggerConfig;

class ResilientLoggerNonRequiredFailureTest {

    /** Always fails. Used to verify that a non-required failure does not abort the batch. */
    static class FailingTarget extends AbstractLogTarget {
        FailingTarget(ComponentConfig config) {
            super(config);
        }

        @Override
        public boolean submit(AbstractLogSource.Entry entry) {
            return false;
        }
    }

    @BeforeEach
    void setup() {
        MockLogSource.reset();
        MockLogTarget.reset();
    }

    @Test
    void nonRequiredTargetFailureDoesNotBlockSuccess() {
        MockLogSource.addEntry("entry-1", AuditLogEvent.builder()
                .operation("OP")
                .message("msg")
                .environment("test")
                .build());

        MockLogSource source = new MockLogSource(
                new ComponentConfig(Map.of("class", MockLogSource.class.getName())));
        FailingTarget nonRequired = new FailingTarget(new ComponentConfig(Map.of(
                "class", FailingTarget.class.getName(),
                "required", false)));
        MockLogTarget required = new MockLogTarget(new ComponentConfig(Map.of(
                "class", MockLogTarget.class.getName(),
                "required", true)));

        ResilientLogger logger = ResilientLogger.create(
                ResilientLoggerConfig.builder().environment("test").origin("test").build(),
                List.of(source),
                List.of(nonRequired, required));

        Map<String, Boolean> results = logger.submitUnsentEntries();

        assertTrue(results.get("entry-1"),
                "Non-required target failure must not stop a required target from receiving the entry");
        assertTrue(MockLogTarget.submittedEntries().stream().anyMatch(e -> e.getId().equals("entry-1")));
    }
}
