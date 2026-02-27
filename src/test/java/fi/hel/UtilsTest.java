package fi.hel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import fi.hel.resilient_logger.utils.Utils;

class UtilsTest {
    @Test
    void shouldCreateDifferentHashes() {
        Map<String, Object> mapA = Map.of("key", "value1");
        Map<String, Object> mapB = Map.of("key", "value2");

        String hashA = Utils.contentHash(mapA);
        String hashB = Utils.contentHash(mapB);

        assertNotEquals(hashA, hashB);
    }

    @Test
    void shouldCreateSameHashWithUnorderedData() {
        Map<String, Object> mapA = new HashMap<>();
        mapA.put("operation", "CREATE");
        mapA.put("actor", "admin");
        mapA.put("timestamp", "2026-02-24T15:00:00Z");

        Map<String, Object> mapB = new HashMap<>();
        mapB.put("timestamp", "2026-02-24T15:00:00Z");
        mapB.put("operation", "CREATE");
        mapB.put("actor", "admin");

        String hashA = Utils.contentHash(mapA);
        String hashB = Utils.contentHash(mapB);

        assertEquals(hashA, hashB);
    }

    @Test
    @DisplayName("Should convert numeric strings to primitives")
    void testNumericConversion() {
        assertAll(
                () -> assertEquals(80, Utils.convertValue("80", int.class)),
                () -> assertEquals(100L, Utils.convertValue(" 100 ", long.class)),
                () -> assertEquals(10.5, Utils.convertValue("10.5", double.class)));
    }

    @Test
    @DisplayName("Should handle 1/0 and true/false for Booleans")
    void testBooleanConversion() {
        assertAll(
                () -> assertTrue(Utils.convertValue("true", boolean.class)),
                () -> assertTrue(Utils.convertValue("1", boolean.class)),
                () -> assertFalse(Utils.convertValue("false", boolean.class)),
                () -> assertFalse(Utils.convertValue("0", boolean.class)),
                () -> assertTrue(Utils.convertValue(1, Boolean.class)),
                () -> assertFalse(Utils.convertValue(0, Boolean.class)));
    }

    @Test
    @DisplayName("Should pass through values that are already the correct type")
    void testIdentityConversion() {
        String str = "Hello World";
        assertEquals(str, Utils.convertValue(str, String.class));

        Integer port = 9000;
        assertEquals(port, Utils.convertValue(port, Integer.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for invalid formats")
    void testInvalidFormats() {
        assertAll(
                () -> assertThrows(NumberFormatException.class,
                        () -> Utils.convertValue("abc", Integer.class)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> Utils.convertValue("not-a-boolean", boolean.class)));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for type mismatches")
    void testTypeMismatch() {
        var list = java.util.List.of(1, 2, 3);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Utils.convertValue(list, Double.class));

        assertTrue(ex.getMessage().contains("For input string"));
    }
}