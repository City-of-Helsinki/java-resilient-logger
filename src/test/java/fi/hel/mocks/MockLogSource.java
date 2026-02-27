package fi.hel.mocks;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import fi.hel.resilient_logger.sources.AbstractLogSource;
import fi.hel.resilient_logger.types.AuditLogDocument;
import fi.hel.resilient_logger.types.AuditLogEvent;
import fi.hel.resilient_logger.types.ComponentConfig;

public class MockLogSource extends AbstractLogSource {
    public MockLogSource(ComponentConfig config) {
        super(config);
    }

    private static List<Entry> entries = new ArrayList<>();

    public static class MockLogEntry implements AbstractLogSource.Entry {
        private String id;
        private AuditLogEvent event;
        private boolean sent;

        public MockLogEntry(String id, AuditLogEvent event) {
            this.id = id;
            this.event = event;
            this.sent = false;
        }

        @Override
        public String getId() {
            return this.id;
        }

        @Override
        public AuditLogDocument getDocument() {
            return AuditLogDocument
                    .builder()
                    .timestamp(event.dateTime().toString())
                    .auditEvent(event)
                    .build();
        }

        @Override
        public boolean isSent() {
            return this.sent;
        }

        @Override
        public void markSent() {
            this.sent = true;
        }
    }

    public static void reset() {
        MockLogSource.entries = new ArrayList<>();
    }

    public static Entry addEntry(String id, AuditLogEvent event) {
        Entry entry = new MockLogEntry(id, event);
        MockLogSource.entries.add(entry);
        return entry;
    }

    @Override
    public Stream<Entry> getUnsentEntries(int chunkSize) {
        return MockLogSource.entries.stream().limit(chunkSize).filter(it -> !it.isSent());
    }

    @Override
    public List<String> clearSentEntries(int retentionDays) {
        List<AbstractLogSource.Entry> toDelete = MockLogSource.entries.stream()
                .filter(it -> it.isSent())
                .toList();

        List<String> ids = toDelete.stream()
                .map(it -> it.getId())
                .toList();

        MockLogSource.entries = new ArrayList<>(MockLogSource.entries.stream()
                .filter(it -> !it.isSent())
                .toList());

        return ids;
    }
}