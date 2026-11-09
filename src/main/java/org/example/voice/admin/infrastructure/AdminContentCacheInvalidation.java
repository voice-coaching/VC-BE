package org.example.voice.admin.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.port.AdminContentInvalidation;
import org.example.voice.practicecontent.infrastructure.cache.PracticeContentCacheNames;
import org.example.voice.home.infrastructure.cache.HomeCacheNames;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

@Component @RequiredArgsConstructor
public class AdminContentCacheInvalidation implements AdminContentInvalidation {
    private final CacheManager caches;
    private final org.example.voice.practicecontent.infrastructure.cache.ContentCatalogCacheCoordinator catalog;
    @Override public void afterContentChange() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                catalog.invalidate();
                for(String name: java.util.List.of(PracticeContentCacheNames.LIST,PracticeContentCacheNames.FACETS,PracticeContentCacheNames.REFERENCE_AUDIO_LIST,PracticeContentCacheNames.DETAIL,PracticeContentCacheNames.NEXT,
                        PracticeContentCacheNames.RECOMMENDATIONS,PracticeContentCacheNames.EXISTS,org.example.voice.mypage.infrastructure.cache.MyPageCacheNames.RECOMMENDATIONS,HomeCacheNames.RECOMMENDATIONS,org.example.voice.course.infrastructure.cache.CourseCacheNames.LIST,org.example.voice.course.infrastructure.cache.CourseCacheNames.DETAIL,org.example.voice.course.infrastructure.cache.CourseCacheNames.STEPS)) {
                    try { var cache=caches.getCache(name); if(cache!=null)cache.clear(); }
                    catch(RuntimeException error) {
                        // The DB/audit transaction is already committed. Never imply a safe write retry.
                        org.slf4j.LoggerFactory.getLogger(getClass()).warn("admin_content_cache_invalidation_failed cache={}",name);
                    }
                }
            }
        });
    }
}
