package fi.hel.resilient_logger.builders;

import java.time.OffsetDateTime;
import java.util.Map;

import fi.hel.resilient_logger.types.AuditLogEvent;

public class AuditLogEventBuilder {
  private Map<String, Object> actor = Map.of();
  private OffsetDateTime dateTime = OffsetDateTime.now();
  private String operation;
  private String origin = "";
  private Map<String, Object> target = Map.of();
  private String environment = "";
  private String message;
  private Integer level = 200;
  private Map<String, Object> extra = Map.of();

  public AuditLogEventBuilder actor(Map<String, Object> actor) {
    this.actor = actor;
    return this;
  }

  public AuditLogEventBuilder dateTime(OffsetDateTime dateTime) {
    this.dateTime = dateTime;
    return this;
  }

  public AuditLogEventBuilder operation(String operation) {
    this.operation = operation;
    return this;
  }

  public AuditLogEventBuilder origin(String origin) {
    this.origin = origin;
    return this;
  }

  public AuditLogEventBuilder target(Map<String, Object> target) {
    this.target = target;
    return this;
  }

  public AuditLogEventBuilder environment(String environment) {
    this.environment = environment;
    return this;
  }

  public AuditLogEventBuilder message(String message) {
    this.message = message;
    return this;
  }

  public AuditLogEventBuilder level(int level) {
    this.level = level;
    return this;
  }

  public AuditLogEventBuilder extra(Map<String, Object> extra) {
    this.extra = extra;
    return this;
  }

  public AuditLogEvent build() {
    return new AuditLogEvent(
        actor,
        dateTime,
        operation,
        origin,
        target,
        environment,
        message,
        level,
        extra);
  }
}
