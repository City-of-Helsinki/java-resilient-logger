package fi.hel;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import fi.hel.resilient_logger.types.ComponentConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ComponentConfigTest {

    private Map<String, Object> options;

    @BeforeEach
    void setUp() {
        options = new HashMap<>();
        options.put("class", "fi.hel.targets.TestTarget");
    }

    @Test
    @DisplayName("Should throw exception if 'class' is missing")
    void testMandatoryClass() {
        Map<String, Object> emptyOptions = Map.of();
        assertThrows(IllegalArgumentException.class, () -> new ComponentConfig(emptyOptions));
    }

    @Nested
    @DisplayName("Integer Leniency Tests")
    class IntegerTests {

        @ParameterizedTest(name = "Input {0} (type {1}) should become 9200")
        @MethodSource("integerProvider")
        void testIntegerLeniency(Object input) {
            options.put("port", input);
            ComponentConfig config = new ComponentConfig(options);

            Integer result = config.getValue("port", Integer.class);

            assertEquals(9200, result);
        }

        static Stream<Arguments> integerProvider() {
            return Stream.of(
                    Arguments.of(9200, "Integer"),
                    Arguments.of(9200L, "Long"),
                    Arguments.of(9200.0, "Double"),
                    Arguments.of("9200", "String"));
        }
    }

    @Test
    @DisplayName("Should return default value when key is missing")
    void testDefaultValueFallback() {
        ComponentConfig config = new ComponentConfig(options);
        String result = config.getValueOrDefault("missing_key", "fallback");
        assertEquals("fallback", result);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on incompatible types")
    void testTypeMismatch() {
        options.put("retries", "five"); // Not a number string
        ComponentConfig config = new ComponentConfig(options);

        assertThrows(IllegalArgumentException.class, () -> config.getValue("retries", Boolean.class));
    }

    @Test
    @DisplayName("Should handle parsing errors for invalid numeric strings")
    void testInvalidNumericString() {
        options.put("port", "not-a-number");
        ComponentConfig config = new ComponentConfig(options);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> config.getValue("port", Integer.class));

        assertTrue(ex.getMessage().contains("Configuration error for key 'port'"));
    }
}