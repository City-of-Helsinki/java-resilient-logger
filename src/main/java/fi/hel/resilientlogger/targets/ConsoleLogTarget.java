package fi.hel.resilientlogger.targets;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;

import fi.hel.resilientlogger.sources.AbstractLogSource.Entry;
import fi.hel.resilientlogger.types.AuditLogDocument;
import fi.hel.resilientlogger.types.AuditLogEvent;
import fi.hel.resilientlogger.types.ComponentConfig;
import fi.hel.resilientlogger.utils.Utils;

public class ConsoleLogTarget extends AbstractLogTarget {
    private static final Logger logger = System.getLogger(ConsoleLogTarget.class.getName());
    private static final Map<Integer, Level> severityToLevel;
    static {
        Map<Integer, Level> byLevel = new HashMap<>();
        for (Level level : Level.values()) {
            byLevel.put(level.getSeverity(), level);
        }
        severityToLevel = Map.copyOf(byLevel);
    }

    private final boolean markAsSent;

    public ConsoleLogTarget(ComponentConfig config) {
        super(config, config.getValueOrDefault("required", false));
        this.markAsSent = config.getValueOrDefault("mark_as_sent", false);
    }

    @Override
    public boolean submit(Entry entry) {
        AuditLogDocument document = entry.getDocument();
        AuditLogEvent event = document.auditEvent();
        Level level = severityToLevel.getOrDefault(event.level(), Level.INFO);

        logger.log(level, "{0}", Utils.toMap(document));
        return markAsSent;
    }

}
