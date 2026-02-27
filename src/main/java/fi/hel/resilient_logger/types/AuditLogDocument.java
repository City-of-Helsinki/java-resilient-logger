package fi.hel.resilient_logger.types;

import com.fasterxml.jackson.annotation.JsonProperty;

import fi.hel.resilient_logger.builders.AuditLogDocumentBuilder;

public record AuditLogDocument(
        @JsonProperty("@timestamp") String timestamp,
        @JsonProperty("audit_event") AuditLogEvent auditEvent) {
    public static AuditLogDocumentBuilder builder() {
        return new AuditLogDocumentBuilder();
    }
}