package fi.hel.mocks;

import java.util.ArrayList;
import java.util.List;

import fi.hel.resilient_logger.sources.AbstractLogSource;
import fi.hel.resilient_logger.targets.AbstractLogTarget;
import fi.hel.resilient_logger.types.ComponentConfig;

public class MockLogTarget extends AbstractLogTarget {
    public MockLogTarget(ComponentConfig config) {
        super(config);
    }

    private static boolean result = true;
    private static List<AbstractLogSource.Entry> entries = new ArrayList<>();

    public static void reset() {
        result = true;
        entries = new ArrayList<>();
    }

    public static void setResult(boolean result) {
        MockLogTarget.result = result;
    }

    public static List<AbstractLogSource.Entry> submittedEntries() {
        return entries;
    }

    @Override
    public boolean submit(AbstractLogSource.Entry entry) {
        if (MockLogTarget.result) {
            MockLogTarget.entries.add(entry);
        }

        return MockLogTarget.result;
    }
}