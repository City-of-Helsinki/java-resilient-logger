package fi.hel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import fi.hel.resilientlogger.sources.AbstractLogSource.Entry;
import fi.hel.resilientlogger.targets.ConsoleLogTarget;
import fi.hel.resilientlogger.types.AuditLogDocument;
import fi.hel.resilientlogger.types.AuditLogEvent;
import fi.hel.resilientlogger.types.ComponentConfig;

class ConsoleLogTargetTest {

    private static final Entry SAMPLE_ENTRY = new Entry() {
        @Override public String getId() { return "id-1"; }
        @Override public boolean isSent() { return false; }
        @Override public void markSent() { }
        @Override public AuditLogDocument getDocument() {
            return AuditLogDocument.builder()
                    .timestamp(OffsetDateTime.now().toString())
                    .auditEvent(AuditLogEvent.builder()
                            .operation("OP")
                            .message("msg")
                            .environment("test")
                            .build())
                    .build();
        }
    };

    @Test
    void defaultsToNotMarkAsSent() {
        ConsoleLogTarget target = new ConsoleLogTarget(
                new ComponentConfig(Map.of("class", ConsoleLogTarget.class.getName())));
        assertFalse(target.submit(SAMPLE_ENTRY));
    }

    @Test
    void markAsSentTrueReturnsTrue() {
        ConsoleLogTarget target = new ConsoleLogTarget(new ComponentConfig(Map.of(
                "class", ConsoleLogTarget.class.getName(),
                "mark_as_sent", true)));
        assertTrue(target.submit(SAMPLE_ENTRY));
    }

    @Test
    void requiredDefaultsToFalse() {
        ConsoleLogTarget target = new ConsoleLogTarget(
                new ComponentConfig(Map.of("class", ConsoleLogTarget.class.getName())));
        assertFalse(target.isRequired());
    }

    @Test
    void requiredHonoredWhenSetToTrue() {
        ConsoleLogTarget target = new ConsoleLogTarget(new ComponentConfig(Map.of(
                "class", ConsoleLogTarget.class.getName(),
                "required", true)));
        assertTrue(target.isRequired());
    }
}
