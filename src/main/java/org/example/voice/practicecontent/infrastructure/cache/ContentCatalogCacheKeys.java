package org.example.voice.practicecontent.infrastructure.cache;

import org.example.voice.practicecontent.domain.model.ContentCatalogData.Filter;

public final class ContentCatalogCacheKeys {
    private ContentCatalogCacheKeys() {}

    public static String list(Filter filter, int page, int size) {
        String category = filter.category();
        if (category != null && category.isBlank()) category = null;
        return key(filter.type(), category, filter.difficulty(), filter.focus(), page, size);
    }

    private static String key(Object... values) {
        StringBuilder key = new StringBuilder("catalog-v1:");
        for (Object value : values) {
            if (value == null) key.append("-1:");
            else {
                String text = value.toString();
                key.append(text.length()).append(':').append(text);
            }
        }
        return key.toString();
    }
}
