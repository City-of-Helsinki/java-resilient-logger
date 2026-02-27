package fi.hel.resilient_logger.types;

import java.time.OffsetDateTime;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;

import fi.hel.resilient_logger.builders.AuditLogEventBuilder;

public record AuditLogEvent(
        Map<String, Object> actor,
        @JsonProperty("date_time") OffsetDateTime dateTime,
        String operation,
        String origin,
        Map<String, Object> target,
        String environment,
        String message,
        int level,
        Map<String, Object> extra) {
    public static AuditLogEventBuilder builder() {
        return new AuditLogEventBuilder();
    }
}
