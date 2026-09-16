package org.example.voice.practicecontent.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.practicecontent.application.ContentCatalogService;
import org.example.voice.practicecontent.domain.model.ContentCatalogData.*;
import org.example.voice.practicecontent.domain.type.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/practice-contents")
public class ContentCatalogController {
    private final ContentCatalogService catalog;
    public record FacetsResponse(ContentType type,List<Facet> categories,List<Facet> difficulties,String revision) {
        static FacetsResponse from(Facets data){return new FacetsResponse(data.type(),data.categories(),data.difficulties(),data.revision());}
    }
    public record AdjacentResponse(Neighbor previous,Neighbor next) {
        static AdjacentResponse from(Adjacent data){return new AdjacentResponse(data.previous(),data.next());}
    }
    @GetMapping("/facets") public ApiResponse<FacetsResponse> facets(@RequestParam ContentType type) {
        return ApiResponse.success("콘텐츠 필터를 조회했습니다.",FacetsResponse.from(catalog.facets(type)));
    }
    @GetMapping("/{contentId}/adjacent") public ApiResponse<AdjacentResponse> adjacent(@PathVariable Long contentId,@RequestParam ContentType type,
            @RequestParam(required=false) String category,@RequestParam(required=false) Difficulty difficulty,@RequestParam(required=false) LearningFocus focus) {
        return ApiResponse.success("이전·다음 콘텐츠를 조회했습니다.",AdjacentResponse.from(catalog.adjacent(contentId,type,category,difficulty,focus)));
    }
}
