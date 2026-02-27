package fi.hel.resilient_logger.targets;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import fi.hel.resilient_logger.sources.AbstractLogSource.Entry;
import fi.hel.resilient_logger.types.AuditLogDocument;
import fi.hel.resilient_logger.types.AuditLogEvent;
import fi.hel.resilient_logger.types.ComponentConfig;
import fi.hel.resilient_logger.utils.Utils;

public class ConsoleLogTarget extends AbstractLogTarget {
    private static final Logger logger = System.getLogger(ConsoleLogTarget.class.getName());
    private static final Map<Integer, Level> severityToLevel = Arrays.stream(Level.values())
        .collect(Collectors.toMap(Level::getSeverity, level -> level));

    public ConsoleLogTarget(ComponentConfig config) {
        super(config, false);
    }

    @Override
    public boolean submit(Entry entry) {
        AuditLogDocument document = entry.getDocument();
        AuditLogEvent event = document.auditEvent();
        Level level = severityToLevel.getOrDefault(event.level(), Level.INFO);

        logger.log(level, "{0}", Utils.toMap(document));
        return false;
    }

}
