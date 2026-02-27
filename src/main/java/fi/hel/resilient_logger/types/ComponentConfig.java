package fi.hel.resilient_logger.types;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;

import fi.hel.resilient_logger.utils.Utils;

public record ComponentConfig(
        String className,
        Map<String, Object> options) {
            
    @JsonCreator
    public ComponentConfig(Map<String, Object> options) {
        this(extractClassName(options), options);
    }

    private static String extractClassName(Map<String, Object> options) {
        Object className = options.get("class");

        if (!(className instanceof String str) || str.isBlank()) {
            throw new IllegalArgumentException("Component configuration missing mandatory 'class' key.");
        }

        return str;
    }

    public <T> T getValue(String key, Class<T> type) {
        Object value = options.get(key);

        if (value == null) {
            return null;
        }

        try {
            return Utils.convertValue(value, type);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("Configuration error for key '%s': %s", key, e.getMessage()), e);
        }
    }

    public <T> T getValueOrDefault(String key, T defaultValue) {
        @SuppressWarnings("unchecked")
        Class<T> defaultClass = (Class<T>) defaultValue.getClass();
        
        T value = getValue(key, defaultClass);
        return (value != null) ? value : defaultValue;
    }
}
