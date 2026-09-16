package org.example.voice.practicecontent.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.ContentCatalogException;
import org.example.voice.practicecontent.domain.model.ContentCatalogData.*;
import org.example.voice.practicecontent.domain.port.ContentCatalogReader;
import org.example.voice.practicecontent.domain.type.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ContentCatalogService {
    private final ContentCatalogReader catalog;
    public Facets facets(ContentType type) {
        if(type==null)throw invalid();return catalog.facets(type);
    }
    public Adjacent adjacent(Long id,ContentType type,String category,Difficulty difficulty,LearningFocus focus) {
        if(id==null || id<=0 || type==null || (category!=null && (category.length()>100 || category.codePoints().anyMatch(Character::isISOControl))))throw invalid();
        return catalog.adjacent(id,new Filter(type,category,difficulty,focus));
    }
    private ContentCatalogException invalid(){return new ContentCatalogException(400,"VALIDATION_ERROR");}
}
