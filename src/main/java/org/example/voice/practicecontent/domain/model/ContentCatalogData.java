package org.example.voice.practicecontent.domain.model;

import org.example.voice.practicecontent.domain.type.*;
import java.util.List;

public final class ContentCatalogData {
    private ContentCatalogData() {}
    public record Filter(ContentType type,String category,Difficulty difficulty,LearningFocus focus) {}
    public record Facet(String value,String label,long count,int order) {}
    public record Facets(ContentType type,List<Facet> categories,List<Facet> difficulties,String revision) {}
    public record Neighbor(Long id,String title) {}
    public record Adjacent(Neighbor previous,Neighbor next) {}
}
