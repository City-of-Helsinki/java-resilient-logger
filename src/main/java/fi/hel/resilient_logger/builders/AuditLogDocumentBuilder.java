package fi.hel.resilient_logger.builders;

import fi.hel.resilient_logger.types.AuditLogDocument;
import fi.hel.resilient_logger.types.AuditLogEvent;

public class AuditLogDocumentBuilder {
  private String timestamp;
  private AuditLogEvent auditEvent;

  public AuditLogDocumentBuilder timestamp(String timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public AuditLogDocumentBuilder auditEvent(AuditLogEvent auditEvent) {
    this.auditEvent = auditEvent;
    return this;
  }

  public AuditLogDocument build() {
    if (timestamp == null || timestamp.isBlank()) {
      throw new IllegalStateException("AuditLogDocument.timestamp must be set to a non-blank value.");
    }
    if (auditEvent == null) {
      throw new IllegalStateException("AuditLogDocument.auditEvent must be set.");
    }

    return new AuditLogDocument(timestamp, auditEvent);
  }
}
