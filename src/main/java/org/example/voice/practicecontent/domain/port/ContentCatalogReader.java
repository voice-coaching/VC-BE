package org.example.voice.practicecontent.domain.port;

import org.example.voice.practicecontent.domain.model.*;
import org.example.voice.practicecontent.domain.model.ContentCatalogData.*;
import org.example.voice.practicecontent.domain.type.ContentType;

public interface ContentCatalogReader {
    PracticeContentPageData<PracticeContentSummaryData> list(Filter filter,int page,int size);
    Facets facets(ContentType type);
    Adjacent adjacent(Long contentId,Filter filter);
}
