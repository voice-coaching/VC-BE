package org.example.voice.common.cache;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class CacheKey {

    private static final String EMPTY_VALUE = "none";
    private static final String DELIMITER = ":";

    public static String join(Object... values) {
        return Arrays.stream(values)
                .map(CacheKey::valueOf)
                .collect(Collectors.joining(DELIMITER));
    }

    private static String valueOf(Object value) {
        if (value == null) {
            return EMPTY_VALUE;
        }
        String text = value.toString();
        if (text.isBlank()) {
            return EMPTY_VALUE;
        }
        return text;
    }

    private CacheKey() {
    }
}
