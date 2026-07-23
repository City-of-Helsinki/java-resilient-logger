package fi.hel;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import fi.hel.resilientlogger.builders.AuditLogDocumentBuilder;
import fi.hel.resilientlogger.builders.AuditLogEventBuilder;
import fi.hel.resilientlogger.types.AuditLogEvent;

class AuditLogBuilderValidationTest {

    @Test
    void eventBuilderRejectsMissingOperation() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AuditLogEventBuilder().message("ok").build());
        assertTrue(ex.getMessage().contains("operation"));
    }

    @Test
    void eventBuilderRejectsBlankOperation() {
        assertThrows(IllegalStateException.class,
                () -> new AuditLogEventBuilder().operation("  ").message("ok").build());
    }

    @Test
    void eventBuilderRejectsMissingMessage() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AuditLogEventBuilder().operation("OP").build());
        assertTrue(ex.getMessage().contains("message"));
    }

    @Test
    void documentBuilderRejectsMissingTimestamp() {
        AuditLogEvent event = new AuditLogEventBuilder()
                .operation("OP")
                .message("msg")
                .build();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AuditLogDocumentBuilder().auditEvent(event).build());
        assertTrue(ex.getMessage().contains("timestamp"));
    }

    @Test
    void documentBuilderRejectsMissingAuditEvent() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AuditLogDocumentBuilder().timestamp("2026-01-01T00:00:00Z").build());
        assertTrue(ex.getMessage().contains("auditEvent"));
    }
}
