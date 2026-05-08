package fi.hel.resilientlogger.targets;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import fi.hel.resilientlogger.sources.AbstractLogSource.Entry;
import fi.hel.resilientlogger.types.AuditLogDocument;
import fi.hel.resilientlogger.types.AuditLogEvent;
import fi.hel.resilientlogger.types.ComponentConfig;
import fi.hel.resilientlogger.utils.Utils;

public class ConsoleLogTarget extends AbstractLogTarget {
    private static final Logger logger = System.getLogger(ConsoleLogTarget.class.getName());
    private static final Map<Integer, Level> severityToLevel = Arrays.stream(Level.values())
        .collect(Collectors.toMap(Level::getSeverity, level -> level));

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
