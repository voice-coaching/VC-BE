package org.example.voice.practicecontent;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.example.voice.admin.application.AdminContentService;
import org.example.voice.admin.domain.model.AdminData.ContentDraft;
import org.example.voice.admin.infrastructure.AdminContentCacheInvalidation;
import org.example.voice.common.security.LoginUser;
import org.example.voice.practicecontent.application.*;
import org.example.voice.practicecontent.controller.dto.PracticeContentQueryConditionDto;
import org.example.voice.practicecontent.domain.entity.*;
import org.example.voice.practicecontent.domain.model.ContentCatalogData.*;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.practicecontent.infrastructure.cache.ContentCatalogCacheCoordinator;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.type.UserRole;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Committed fixtures exercise the production Redis serializer and transaction callbacks. */
@SpringBootTest(properties={"custom-content.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.jpa.properties.hibernate.generate_statistics=true","logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="VC_BE_TEST_REDIS_HOST",matches=".+")
class ContentCatalogCacheIntegrationTest {
    @DynamicPropertySource static void redis(DynamicPropertyRegistry r){
        r.add("spring.data.redis.host",()->System.getenv("VC_BE_TEST_REDIS_HOST"));
        r.add("spring.data.redis.port",()->System.getenv("VC_BE_TEST_REDIS_PORT"));
        r.add("spring.data.redis.ssl.enabled",()->true);
        r.add("spring.data.redis.timeout",()->"2s");
    }
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PracticeContentService contents;
    @Autowired ContentCatalogService catalog;
    @Autowired ContentCatalogCacheCoordinator generations;
    @Autowired AdminContentCacheInvalidation invalidation;
    @Autowired AdminContentService admin;
    @Autowired MockMvc mvc;
    @MockitoBean Clock clock;
    static final OffsetDateTime NOW=OffsetDateTime.parse("2030-01-01T00:00:00Z");
    String category;
    Long actor,id;
    final List<Long> fixtures=new ArrayList<>();

    @BeforeEach void setup(){
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);when(clock.instant()).thenReturn(NOW.toInstant());
        category="CACHE_"+UUID.randomUUID().toString().replace("-","");
        write(()->{
            var user=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","cache-test",NOW);
            ReflectionTestUtils.setField(user,"role",UserRole.ADMIN);em.persist(user);actor=user.getId();
            var taxonomy=construct(ContentCategory.class);
            for(var field:Map.<String,Object>of("contentType",ContentType.NEWS,"code",category,"label","Cache test","sortOrder",100).entrySet())
                ReflectionTestUtils.setField(taxonomy,field.getKey(),field.getValue());
            em.persist(taxonomy);
            var content=create(ContentType.NEWS,"Original",NOW.minusDays(1));id=content.getId();
        });generations.invalidate();
    }

    @AfterEach void cleanup(){
        write(()->{for(Long fixture:fixtures){var c=em.find(PracticeContent.class,fixture);if(c!=null)ReflectionTestUtils.setField(c,"status",PublishStatus.HIDDEN);}});
        generations.invalidate();
    }

    @Test void realRedisHitsRemoveListAndFacetQueriesAndKeepHttpShape() throws Exception {
        var stats=emf.unwrap(SessionFactory.class).getStatistics();stats.clear();
        var first=contents.getPracticeContents(condition());assertThat(first.totalElements()).isEqualTo(1);
        assertThat(stats.getQueryExecutionCount()).isEqualTo(2);
        stats.clear();var hit=contents.getPracticeContents(condition());
        assertThat(hit).isEqualTo(first);assertThat(hit.items().getFirst().title()).isEqualTo("Original");
        assertThat(stats.getQueryExecutionCount()).isZero();
        stats.clear();var facets=catalog.facets(ContentType.NEWS);
        assertThat(stats.getQueryExecutionCount()).isEqualTo(2);
        stats.clear();assertThat(catalog.facets(ContentType.NEWS)).isEqualTo(facets);
        assertThat(stats.getQueryExecutionCount()).isZero();
        mvc.perform(get("/api/practice-contents").with(authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(actor),null,List.of())))
                .param("type","NEWS").param("category",category).param("size","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value(true))
                .andExpect(jsonPath("$.data.items[0].title").value("Original"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items[0].scriptText").doesNotExist());
    }

    @Test void adminEditHidePublishAndDeleteInvalidateOnlyCommittedResults() {
        contents.getPracticeContents(condition());catalog.facets(ContentType.NEWS);
        long version=readVersion();
        var edited=admin.edit(actor,id,version,draft("Edited"),"cache verification");
        assertThat(contents.getPracticeContents(condition()).items().getFirst().title()).isEqualTo("Edited");
        String before=catalog.facets(ContentType.NEWS).revision();
        var hidden=admin.publication(actor,id,edited.version(),PublishStatus.HIDDEN,"cache verification");
        assertThat(contents.getPracticeContents(condition()).items()).isEmpty();
        assertThat(catalog.facets(ContentType.NEWS).revision()).isNotEqualTo(before);
        var published=admin.publication(actor,id,hidden.version(),PublishStatus.PUBLISHED,"cache verification");
        assertThat(contents.getPracticeContents(condition()).totalElements()).isEqualTo(1);
        contents.getPracticeContents(condition());String generation=generations.generation();
        var rollback=new TransactionTemplate(transactions);
        rollback.executeWithoutResult(status->{admin.edit(actor,id,published.version(),draft("Rolled back"),"cache verification");status.setRollbackOnly();});
        assertThat(generations.generation()).isEqualTo(generation);
        assertThat(contents.getPracticeContents(condition()).items().getFirst().title()).isEqualTo("Edited");
        admin.delete(actor,id,published.version(),"cache verification");
        assertThat(contents.getPracticeContents(condition()).items()).isEmpty();
    }

    @Test void scheduledPublicationChangesCountsAtExactTimeAndPrivateDeletedRowsStayExcluded() {
        write(()->{
            create(ContentType.NEWS,"Future",NOW.plusSeconds(30));
            var deleted=create(ContentType.NEWS,"Deleted",NOW.minusDays(1));ReflectionTestUtils.setField(deleted,"deletedAt",NOW);
            var privateContent=PracticeContent.custom(actor,"Private title","Private script",LearningFocus.BOTH,NOW);em.persist(privateContent);fixtures.add(privateContent.getId());
            ReflectionTestUtils.setField(privateContent,"category",category);ReflectionTestUtils.setField(privateContent,"contentType",ContentType.NEWS);
            invalidation.afterContentChange();
        });
        assertThat(contents.getPracticeContents(condition()).totalElements()).isEqualTo(1);
        var initial=catalog.facets(ContentType.NEWS);
        assertThat(facet(initial).count()).isEqualTo(1);
        when(clock.instant()).thenReturn(NOW.plusSeconds(30).toInstant());
        var published=contents.getPracticeContents(condition());
        assertThat(published.totalElements()).isEqualTo(2);
        assertThat(published.items().stream().map(i->i.title())).containsExactly("Future","Original");
        var changed=catalog.facets(ContentType.NEWS);
        assertThat(facet(changed).count()).isEqualTo(2);assertThat(changed.revision()).isNotEqualTo(initial.revision());
    }

    @Test void combinedFacetAggregationPreservesCategoryAndDifficultyTotals() {
        var initial=catalog.facets(ContentType.NEWS);
        write(()->{
            var advanced=create(ContentType.NEWS,"Advanced",NOW.minusDays(1));ReflectionTestUtils.setField(advanced,"difficulty",Difficulty.ADVANCED);
            var uncategorized=create(ContentType.NEWS,"No category",NOW.minusDays(1));ReflectionTestUtils.setField(uncategorized,"category",null);
            var blank=create(ContentType.NEWS,"Blank category",NOW.minusDays(1));ReflectionTestUtils.setField(blank,"category","   ");
            create(ContentType.NEWS,"Future advanced",NOW.plusMinutes(1));
            invalidation.afterContentChange();
        });
        var after=catalog.facets(ContentType.NEWS);
        assertThat(facet(after).count()).isEqualTo(2);
        assertThat(after.categories()).noneMatch(c->c.value()==null || c.value().isEmpty() || c.value().equals("   "));
        assertThat(difficulty(after,"BEGINNER")-difficulty(initial,"BEGINNER")).isEqualTo(2);
        assertThat(difficulty(after,"ADVANCED")-difficulty(initial,"ADVANCED")).isEqualTo(1);
    }

    @Test void retiredAudioIsExcludedAndAudioChangesRefreshCachedSpeaker() {
        Long audioContent=new TransactionTemplate(transactions).execute(s->{
            var c=create(ContentType.ANNOUNCER,"Audio",NOW.minusDays(1));
            var retired=ReferenceAudio.announcer(c,"Retired","retired.wav",1000,true,NOW);ReflectionTestUtils.setField(retired,"retiredAt",NOW);em.persist(retired);
            em.persist(ReferenceAudio.announcer(c,"Current","current.wav",1000,false,NOW));return c.getId();
        });generations.invalidate();
        var query=new PracticeContentQueryConditionDto(ContentType.ANNOUNCER,category,null,null,0,10);
        assertThat(contents.getPracticeContents(query).items().getFirst().speakerName()).isEqualTo("Current");
        write(()->{var c=em.find(PracticeContent.class,audioContent);em.persist(ReferenceAudio.announcer(c,"New primary","new.wav",1000,true,NOW));invalidation.afterContentChange();});
        assertThat(contents.getPracticeContents(query).items().getFirst().speakerName()).isEqualTo("New primary");
    }

    Facet facet(Facets f){return f.categories().stream().filter(c->c.value().equals(category)).findFirst().orElseThrow();}
    long difficulty(Facets f,String value){return f.difficulties().stream().filter(d->d.value().equals(value)).mapToLong(Facet::count).sum();}
    PracticeContentQueryConditionDto condition(){return new PracticeContentQueryConditionDto(ContentType.NEWS,category,null,null,0,10);}
    ContentDraft draft(String title){return new ContentDraft(ContentType.NEWS,LearningFocus.BOTH,category,Difficulty.BEGINNER,title,null,"Public script.",List.of(),30);}
    long readVersion(){return new TransactionTemplate(transactions).execute(s->em.find(PracticeContent.class,id).getVersion());}
    void write(Runnable action){new TransactionTemplate(transactions).executeWithoutResult(s->action.run());}
    PracticeContent create(ContentType type,String title,OffsetDateTime published){
        var c=PracticeContent.draft(type,LearningFocus.BOTH,category,Difficulty.BEGINNER,title,null,"Public script.",List.of(),30,NOW.minusDays(2));
        c.changePublication(PublishStatus.PUBLISHED,published);em.persist(c);fixtures.add(c.getId());return c;
    }
    static <T> T construct(Class<T> type){try{var c=type.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}catch(Exception e){throw new AssertionError(e);}}
}
