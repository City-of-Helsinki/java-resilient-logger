package fi.hel.resilient_logger.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Utils {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private static final TypeReference<Map<String, Object>> typeReference = new TypeReference<>() {
    };

    private static final Map<Class<?>, Class<?>> typeWrappers = Map.of(
            int.class, Integer.class,
            long.class, Long.class,
            double.class, Double.class,
            float.class, Float.class,
            boolean.class, Boolean.class);

    private static final Map<Class<?>, Function<Object, Object>> typeConverters = Map.of(
            Integer.class, v -> v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString().trim()),
            Long.class, v -> v instanceof Number n ? n.longValue() : Long.parseLong(v.toString().trim()),
            Double.class, v -> v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString().trim()),
            Boolean.class, v -> v instanceof Boolean b ? b : parseBooleanStrict(v.toString().trim()));

    public static <T> T convertValue(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }

        Class<?> wrapperType = typeWrappers.getOrDefault(type, type);
        Function<Object, Object> converter = typeConverters.getOrDefault(wrapperType, v -> v);

        Object converted = converter.apply(value);

        if (!wrapperType.isInstance(converted)) {
            throw new IllegalArgumentException(String.format(
                    "Expected type %s but found %s",
                    type.getSimpleName(), converted.getClass().getSimpleName()));
        }

        @SuppressWarnings("unchecked")
        T result = (T) converted;

        return result;
    }

    public static boolean parseBooleanStrict(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }

        if (value instanceof Number n) {
            if (n.intValue() == 1) {
                return true;
            }

            if (n.intValue() == 0) {
                return false;
            }
        }

        String s = value.toString().trim().toLowerCase();
        return switch (s) {
            case "true", "1" -> true;
            case "false", "0" -> false;
            default -> throw new IllegalArgumentException("Invalid boolean: " + s);
        };
    }

    public static Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return Collections.emptyMap();
        }

        return objectMapper.convertValue(obj, typeReference);
    }

    public static String contentHash(Map<String, Object> contents) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(contents);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(jsonBytes);

            return HexFormat.of().formatHex(encodedHash);

        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate content hash", e);
        }
    }

    public static <T> Class<? extends T> tryLoadClass(String className, Class<T> baseClass) throws Exception {
        try {
            Class<?> cls = Class.forName(className);

            if (baseClass.isAssignableFrom(cls)) {
                return cls.asSubclass(baseClass);
            } else {
                throw new ClassCastException(String.format(
                        "Class %s does not extend/implement %s", className, baseClass.getName()));
            }
        } catch (ClassNotFoundException e) {
            throw e;
        }
    }

    public static <T> T instantiate(String className, Class<T> baseClass, Object... ctorArgs) {
        Class<?>[] argTypes = new Class<?>[ctorArgs.length];

        for (int i = 0; i < ctorArgs.length; i++) {
            argTypes[i] = ctorArgs[i].getClass();
        }

        try {
            Class<? extends T> cls = Utils.tryLoadClass(className, baseClass);
            return cls.getDeclaredConstructor(argTypes).newInstance(ctorArgs);
        } catch (NoSuchMethodException e) {
            String paramTypesStr = Arrays.stream(argTypes)
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", "));
            throw new RuntimeException(String.format(
                    "No constructor found with matching types for %s for arguments: (%s)",
                    className, paramTypesStr));
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + className, e);
        }
    }
}