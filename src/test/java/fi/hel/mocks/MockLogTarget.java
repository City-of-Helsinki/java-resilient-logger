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

    private static boolean required = true;
    private static boolean result = true;
    private static List<AbstractLogSource.Entry> entries = new ArrayList<>();

    public static void reset() {
        required = true;
        result = true;
        entries = new ArrayList<>();
    }

    public static void setRequired(boolean required) {
        MockLogTarget.required = required;
    }

    public static void setResult(boolean result) {
        MockLogTarget.result = result;
    }

    @Override
    public boolean isRequired() {
        return MockLogTarget.required;
    }

    @Override
    public boolean submit(AbstractLogSource.Entry entry) {
        if (MockLogTarget.result) {
            MockLogTarget.entries.add(entry);
        }

        return MockLogTarget.result;
    }
}