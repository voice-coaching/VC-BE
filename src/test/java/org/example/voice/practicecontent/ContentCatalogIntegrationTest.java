package org.example.voice.practicecontent;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.practicecontent.application.*;
import org.example.voice.practicecontent.controller.dto.PracticeContentQueryConditionDto;
import org.example.voice.practicecontent.domain.ContentCatalogException;
import org.example.voice.practicecontent.domain.entity.*;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="custom-content.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc @Transactional
class ContentCatalogIntegrationTest {
    @Autowired EntityManager em;
    @Autowired ContentCatalogService catalog;
    @Autowired PracticeContentService contents;
    @Autowired MockMvc mvc;
    private static final OffsetDateTime PUBLISHED=OffsetDateTime.parse("2026-01-01T00:00:00Z");
    private static final OffsetDateTime CREATED=OffsetDateTime.parse("2025-12-01T00:00:00Z");
    Long user;
    @BeforeEach void setup(){
        var owner=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","catalog",CREATED);em.persist(owner);user=owner.getId();
        if(em.createQuery("select count(t) from ContentCategory t where t.contentType=:type and t.code='SOCIETY'",Long.class).setParameter("type",ContentType.NEWS).getSingleResult()==0){
            var category=construct(ContentCategory.class);
            fields(category,Map.of("contentType",ContentType.NEWS,"code","SOCIETY","label","사회","sortOrder",1));em.persist(category);
        }
    }
    @Test void adjacentMatchesPagedListForTiedAndNullDatesInBothDirections(){
        var a=create(ContentType.NEWS,"SOCIETY",PUBLISHED,CREATED);
        var b=create(ContentType.NEWS,"SOCIETY",PUBLISHED,CREATED);
        var c=create(ContentType.NEWS,"SOCIETY",PUBLISHED.minusDays(1),CREATED.plusDays(1));
        var d=create(ContentType.NEWS,"SOCIETY",null,CREATED);
        var e=create(ContentType.NEWS,"SOCIETY",null,CREATED.plusDays(1));
        var f=create(ContentType.NEWS,"SOCIETY",null,CREATED.plusDays(1));
        List<Long> expected=List.of(b.getId(),a.getId(),c.getId(),f.getId(),e.getId(),d.getId());
        var page=contents.getPracticeContents(condition(ContentType.NEWS,"SOCIETY",0,100));
        assertThat(page.items().stream().map(i->i.id())).containsExactlyElementsOf(expected);
        for(int i=0;i<expected.size();i++){
            var adjacent=catalog.adjacent(expected.get(i),ContentType.NEWS,"SOCIETY",Difficulty.BEGINNER,LearningFocus.BOTH);
            assertThat(adjacent.previous()==null?null:adjacent.previous().id()).isEqualTo(i==0?null:expected.get(i-1));
            assertThat(adjacent.next()==null?null:adjacent.next().id()).isEqualTo(i==expected.size()-1?null:expected.get(i+1));
        }
        var first=contents.getPracticeContents(condition(ContentType.NEWS,"SOCIETY",0,2));
        var second=contents.getPracticeContents(condition(ContentType.NEWS,"SOCIETY",1,2));
        var last=contents.getPracticeContents(condition(ContentType.NEWS,"SOCIETY",2,2));
        assertThat(first.hasNext()).isTrue();assertThat(last.hasNext()).isFalse();
        assertThat(catalog.adjacent(first.items().getLast().id(),ContentType.NEWS,"SOCIETY",null,null).next().id()).isEqualTo(second.items().getFirst().id());
    }
    @Test void facetsCountOnlyVisibleContentAndRevisionChangesWithCountsAndLabels(){
        create(ContentType.NEWS,"SOCIETY",PUBLISHED,CREATED);
        var hidden=create(ContentType.NEWS,"SOCIETY",PUBLISHED,CREATED);ReflectionTestUtils.setField(hidden,"status",PublishStatus.HIDDEN);
        create(ContentType.NEWS,"SOCIETY",OffsetDateTime.now().plusYears(1),CREATED);
        var custom=PracticeContent.custom(user,"비공개 제목","내 원고",LearningFocus.BOTH,CREATED);em.persist(custom);
        var first=catalog.facets(ContentType.NEWS);
        assertThat(first.categories()).hasSize(1);assertThat(first.categories().getFirst().label()).isEqualTo("사회");
        assertThat(first.categories().getFirst().count()).isEqualTo(1);
        assertThat(first.difficulties().getFirst().label()).isEqualTo("초급");
        assertThat(catalog.facets(ContentType.NEWS).revision()).isEqualTo(first.revision());
        create(ContentType.NEWS,"SOCIETY",PUBLISHED,CREATED);
        assertThat(catalog.facets(ContentType.NEWS).revision()).isNotEqualTo(first.revision());
        assertThat(catalog.facets(ContentType.SENTENCE).categories()).noneMatch(t->t.value().equals("CUSTOM"));
        var tax=em.createQuery("select t from ContentCategory t where t.contentType=:type and t.code='SOCIETY'",ContentCategory.class).setParameter("type",ContentType.NEWS).getSingleResult();
        var before=catalog.facets(ContentType.NEWS).revision();ReflectionTestUtils.setField(tax,"label","사회 뉴스");
        assertThat(catalog.facets(ContentType.NEWS).revision()).isNotEqualTo(before);
    }
    @Test void rejectsInvisibleAndOutOfFilterAnchors(){
        var c=create(ContentType.NEWS,"SOCIETY",PUBLISHED,CREATED);
        assertMissing(()->catalog.adjacent(c.getId(),ContentType.NEWS,"ECONOMY",null,null));
        assertMissing(()->catalog.adjacent(c.getId(),ContentType.NEWS,null,Difficulty.ADVANCED,null));
        assertMissing(()->catalog.adjacent(c.getId(),ContentType.NEWS,null,null,LearningFocus.INTONATION));
        ReflectionTestUtils.setField(c,"status",PublishStatus.HIDDEN);
        assertMissing(()->catalog.adjacent(c.getId(),ContentType.NEWS,null,null,null));
        var custom=PracticeContent.custom(user,"원고","비밀",LearningFocus.BOTH,CREATED);em.persist(custom);
        assertMissing(()->catalog.adjacent(custom.getId(),ContentType.SENTENCE,null,null,null));
    }
    @Test void returnsRealTextMetadataAndPrimaryAudioSpeaker(){
        var news=create(ContentType.NEWS,"SOCIETY",PUBLISHED,CREATED);
        ReflectionTestUtils.setField(news,"publisher","등록된 발행사");
        ReflectionTestUtils.setField(news,"scriptText","첫 문장입니다. 둘째 문장입니다.\n\n마지막 문장입니다.");
        var item=contents.getPracticeContents(condition(ContentType.NEWS,"SOCIETY",0,10)).items().getFirst();
        assertThat(item.publisher()).isEqualTo("등록된 발행사");assertThat(item.paragraphCount()).isEqualTo(2);assertThat(item.sentenceCount()).isEqualTo(3);
        assertThat(item.publishedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        var sentence=create(ContentType.SENTENCE,"SYLLABLE_TEST",null,CREATED);
        ReflectionTestUtils.setField(sentence,"title","99음절 제목");ReflectionTestUtils.setField(sentence,"scriptText","가 나! 😀 ABC 다");
        assertThat(contents.getPracticeContents(condition(ContentType.SENTENCE,"SYLLABLE_TEST",0,10)).items().getFirst().syllableCount()).isEqualTo(3);
        var announcer=create(ContentType.ANNOUNCER,"SPEAKER_TEST",PUBLISHED,CREATED);
        audio(announcer,"보조 화자",false);audio(announcer,"대표 화자",true);
        assertThat(contents.getPracticeContents(condition(ContentType.ANNOUNCER,"SPEAKER_TEST",0,10)).items().getFirst().speakerName()).isEqualTo("대표 화자");
        ReflectionTestUtils.setField(announcer,"speakerName","명시한 화자");
        assertThat(contents.getPracticeContents(condition(ContentType.ANNOUNCER,"SPEAKER_TEST",0,10)).items().getFirst().speakerName()).isEqualTo("명시한 화자");
    }
    @Test void emptyAndUnknownCategoriesRemainHonest(){
        assertThat(catalog.facets(ContentType.NEWS).categories()).isEmpty();
        create(ContentType.NEWS,"NEW_CODE",PUBLISHED,CREATED);
        var facets=catalog.facets(ContentType.NEWS);assertThat(facets.categories().getFirst().label()).isEqualTo("NEW_CODE");
        var item=contents.getPracticeContents(condition(ContentType.NEWS,"NEW_CODE",0,10)).items().getFirst();
        assertThat(item.publisher()).isNull();
        var adjacent=catalog.adjacent(item.id(),ContentType.NEWS,"NEW_CODE",null,null);
        assertThat(adjacent.previous()).isNull();assertThat(adjacent.next()).isNull();
    }
    @Test void httpAuthenticationValidationAndPageContract() throws Exception {
        var auth=authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(user),null,List.of()));
        mvc.perform(get("/api/practice-contents/facets").param("type","NEWS")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/practice-contents/facets").with(auth)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/practice-contents/facets").with(auth).param("type","INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/practice-contents/0/adjacent").with(auth).param("type","NEWS")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/practice-contents/99999999/adjacent").with(auth).param("type","NEWS")).andExpect(status().isNotFound());
        mvc.perform(get("/api/practice-contents/facets").with(auth).param("type","NEWS")).andExpect(status().isOk()).andExpect(jsonPath("$.data.categories").isArray());
        mvc.perform(get("/api/practice-contents").with(auth).param("type","NEWS")).andExpect(status().isOk()).andExpect(jsonPath("$.data.hasNext").value(false));
    }
    private PracticeContent create(ContentType type,String category,OffsetDateTime published,OffsetDateTime created){
        var c=construct(PracticeContent.class);fields(c,Map.of("contentType",type,"learningFocus",LearningFocus.BOTH,"title","실제 원고 제목",
                "category",category,"scriptText","기본 문장입니다.","difficulty",Difficulty.BEGINNER,"status",PublishStatus.PUBLISHED,"createdAt",created,"updatedAt",created));
        ReflectionTestUtils.setField(c,"publishedAt",published);em.persist(c);return c;
    }
    private void audio(PracticeContent content,String name,boolean primary){var a=construct(ReferenceAudio.class);fields(a,Map.of("content",content,"speakerName",name,"audioUrl","https://example.invalid/test.mp3","primary",primary,"createdAt",CREATED));em.persist(a);}
    private PracticeContentQueryConditionDto condition(ContentType type,String category,int page,int size){return new PracticeContentQueryConditionDto(type,category,null,null,page,size);}
    private void assertMissing(Runnable run){assertThatThrownBy(run::run).isInstanceOfSatisfying(ContentCatalogException.class,e->assertThat(e.status()).isEqualTo(404));}
    private static void fields(Object target,Map<String,Object> values){values.forEach((k,v)->ReflectionTestUtils.setField(target,k,v));}
    private static <T> T construct(Class<T> type){try{var c=type.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}catch(Exception e){throw new AssertionError(e);}}
}
