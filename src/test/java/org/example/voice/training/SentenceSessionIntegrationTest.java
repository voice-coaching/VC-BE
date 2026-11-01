package org.example.voice.training;

import jakarta.persistence.EntityManager;
import org.example.voice.training.application.TrainingSessionService;
import org.example.voice.training.controller.dto.TrainingSessionCreateRequestDto;
import org.example.voice.training.domain.entity.*;
import org.example.voice.training.domain.model.SentenceTranscript;
import org.example.voice.training.domain.port.VoiceRecordingReader;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.user.domain.entity.User;
import org.example.voice.common.exception.BaseException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @Transactional
class SentenceSessionIntegrationTest {
    @Autowired EntityManager em;
    @Autowired TrainingSessionService sessions;
    @Autowired VoiceRecordingReader recordings;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    Long user, content;

    @BeforeEach void setup() throws Exception {
        var caches=new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
        when(cacheManager.getCache(anyString())).thenAnswer(call->caches.getCache(call.getArgument(0)));
        var now=OffsetDateTime.now();
        var owner=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","sentence user",now);
        em.persist(owner); user=owner.getId();
        var constructor=PracticeContent.class.getDeclaredConstructor(); constructor.setAccessible(true);
        var c=constructor.newInstance();
        Map<String,Object> values=Map.of("contentType",ContentType.NEWS,"learningFocus",LearningFocus.PRONUNCIATION,
            "title","Two sentences","scriptText","First sentence. Second sentence!","difficulty",Difficulty.BEGINNER,
            "status",PublishStatus.PUBLISHED,"createdAt",now,"updatedAt",now);
        values.forEach((key,value)->ReflectionTestUtils.setField(c,key,value));
        em.persist(c); content=c.getId(); em.flush();
    }

    @Test void independentlyBindsEachSentenceAndTheWorkerReadsOnlyItsTranscript() {
        var first=create(0,"First sentence.");
        var second=create(1,"Second sentence!");
        assertThat(first).isNotEqualTo(second);
        for(var id:List.of(first,second)) {
            var session=em.find(TrainingSession.class,id);
            var recording=VoiceRecording.create(session,1,"test-"+id,"audio/wav",100L,3000,
                "a".repeat(64),RecordingQualityStatus.PASS,BigDecimal.ONE,BigDecimal.ONE);
            recording.select(); em.persist(recording);
        }
        em.flush(); em.clear();
        var a=recordings.findSelectedForAnalysis(first,user).orElseThrow();
        var b=recordings.findSelectedForAnalysis(second,user).orElseThrow();
        assertThat(a.scriptText()).isEqualTo("First sentence.");
        assertThat(b.scriptText()).isEqualTo("Second sentence!");
        assertThat(a.promptRevision()).isNotEqualTo(b.promptRevision());
        var detail=sessions.getSession(second,user).content();
        assertThat(detail.scriptText()).isEqualTo("Second sentence!");
        assertThat(detail.sentenceIndex()).isEqualTo(1);
        assertThat(org.example.voice.training.controller.dto.TrainingSessionContentDto.from(detail).sentenceIndex()).isEqualTo(1);
    }

    @Test void rejectsClientTranscriptTamperingAndTitleExamSlicing() {
        assertThatThrownBy(()->create(0,"Second sentence!")).isInstanceOf(BaseException.class);
        assertThatThrownBy(()->create(2,"Invented")).isInstanceOf(BaseException.class);
        assertThatThrownBy(()->sessions.create(new TrainingSessionCreateRequestDto(content,null,
            LearningFocus.PRONUNCIATION,9L,0,"First sentence."),user)).isInstanceOf(BaseException.class);
    }

    @Test void editedSourceCannotSilentlyAnalyzeADifferentSentence() {
        var id=create(1,"Second sentence!");
        var session=em.find(TrainingSession.class,id);
        ReflectionTestUtils.setField(session.getContent(),"scriptText","First sentence. Replacement.");
        assertThatThrownBy(session::analysisScriptText).isInstanceOf(BaseException.class);
    }

    @Test void boundariesMatchReaderIncludingDecimalsAndClosingQuotes() {
        assertThat(SentenceTranscript.split("  Price 3.14. \"Yes!\"  Next\nline? Tail"))
            .containsExactly("Price 3.14.","\"Yes!\"","Next line?","Tail");
    }

    Long create(int index,String text) {
        return sessions.create(new TrainingSessionCreateRequestDto(content,null,
            LearningFocus.PRONUNCIATION,null,index,text),user).sessionId();
    }
}
