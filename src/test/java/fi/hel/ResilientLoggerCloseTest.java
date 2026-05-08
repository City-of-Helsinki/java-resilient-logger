package fi.hel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.hel.mocks.MockLogSource;
import fi.hel.mocks.MockLogTarget;
import fi.hel.resilientlogger.ResilientLogger;
import fi.hel.resilientlogger.sources.AbstractLogSource;
import fi.hel.resilientlogger.targets.AbstractLogTarget;
import fi.hel.resilientlogger.types.ComponentConfig;
import fi.hel.resilientlogger.types.ResilientLoggerConfig;

class ResilientLoggerCloseTest {

    static class CountingSource extends MockLogSource {
        final AtomicInteger closes = new AtomicInteger();

        CountingSource(ComponentConfig config) {
            super(config);
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }

    static class CountingTarget extends MockLogTarget {
        final AtomicInteger closes = new AtomicInteger();

        CountingTarget(ComponentConfig config) {
            super(config);
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }

    static class ThrowingTarget extends AbstractLogTarget {
        ThrowingTarget(ComponentConfig config) {
            super(config);
        }

        @Override
        public boolean submit(AbstractLogSource.Entry entry) {
            return true;
        }

        @Override
        public void close() {
            throw new RuntimeException("simulated close failure");
        }
    }

    @BeforeEach
    void setup() {
        MockLogSource.reset();
        MockLogTarget.reset();
    }

    @Test
    void closeCascadesToSourcesAndTargetsExactlyOnce() {
        CountingSource source = new CountingSource(
                new ComponentConfig(Map.of("class", MockLogSource.class.getName())));
        CountingTarget target = new CountingTarget(
                new ComponentConfig(Map.of("class", MockLogTarget.class.getName())));

        ResilientLogger logger = ResilientLogger.create(
                ResilientLoggerConfig.builder().environment("test").origin("test").build(),
                List.of(source),
                List.of(target));

        logger.close();

        assertEquals(1, source.closes.get());
        assertEquals(1, target.closes.get());
    }

    @Test
    void oneTargetThrowingDoesNotPreventOtherCloses() {
        CountingSource source = new CountingSource(
                new ComponentConfig(Map.of("class", MockLogSource.class.getName())));
        ThrowingTarget bad = new ThrowingTarget(
                new ComponentConfig(Map.of("class", ThrowingTarget.class.getName())));
        CountingTarget good = new CountingTarget(
                new ComponentConfig(Map.of("class", MockLogTarget.class.getName())));

        ResilientLogger logger = ResilientLogger.create(
                ResilientLoggerConfig.builder().environment("test").origin("test").build(),
                List.of(source),
                List.of(bad, good));

        assertDoesNotThrow(logger::close);

        assertEquals(1, good.closes.get(), "good target should still close");
        assertEquals(1, source.closes.get(), "source should still close after target failure");
    }
}
