package org.example.voice.home.domain.model;

import java.util.List;

public record RecommendationListData(
        List<RecommendationItemData> items
) {
}
