package org.example.voice.training.infrastructure;

import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.domain.port.RecordingDeletionScheduler;
import org.example.voice.training.domain.port.RecordingMediaPreparationStore;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.example.voice.practicecontent.infrastructure.PrivateTextConverter;
import org.example.voice.practicecontent.domain.port.PrivateTextCipher;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoiceRecordingSelectionIntegrationTest {
    SessionFactory factory;
    Session em;
    VoiceRecordingWriterImpl writer;
    Long sessionId, firstId, secondId;

    @BeforeEach void setup() throws Exception {
        Configuration config = new Configuration()
                .addAnnotatedClass(TrainingSession.class)
                .addAnnotatedClass(VoiceRecording.class)
                .addAnnotatedClass(PracticeContent.class);
        config.addAttributeConverter(new PrivateTextConverter(mock(PrivateTextCipher.class)), true);
        config.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        config.setProperty("hibernate.connection.url", "jdbc:h2:mem:selection_" + UUID.randomUUID() + ";MODE=PostgreSQL");
        config.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        factory = config.buildSessionFactory();
        em = factory.openSession();
        em.beginTransaction();
        // H2's generated nullable key enforces the same active-selection uniqueness
        // as PostgreSQL's production partial index, including immediate checks.
        em.createNativeMutationQuery("alter table voice_recordings add selected_session bigint generated always as "
                + "(case when is_selected = true and deleted_at is null then training_session_id else null end)").executeUpdate();
        em.createNativeMutationQuery("create unique index test_selected_session on voice_recordings(selected_session)").executeUpdate();
        var constructor = PracticeContent.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var content = constructor.newInstance();
        ReflectionTestUtils.setField(content, "contentType", ContentType.SENTENCE);
        ReflectionTestUtils.setField(content, "learningFocus", LearningFocus.PRONUNCIATION);
        ReflectionTestUtils.setField(content, "title", "selection test");
        ReflectionTestUtils.setField(content, "scriptText", "selection test");
        ReflectionTestUtils.setField(content, "difficulty", Difficulty.BEGINNER);
        ReflectionTestUtils.setField(content, "status", PublishStatus.PUBLISHED);
        ReflectionTestUtils.setField(content, "createdAt", OffsetDateTime.now());
        ReflectionTestUtils.setField(content, "updatedAt", OffsetDateTime.now());
        em.persist(content);
        var session = TrainingSession.create(7L, content, null, LearningFocus.PRONUNCIATION);
        em.persist(session);
        var first = recording(session, 1);
        first.select();
        var second = recording(session, 2);
        em.persist(first);
        em.persist(second);
        em.flush();
        sessionId = session.getId(); firstId = first.getId(); secondId = second.getId();
        em.getTransaction().commit();
        em.clear();
        var sessions = mock(TrainingSessionJpaRepository.class);
        var recordings = mock(VoiceRecordingJpaRepository.class);
        when(sessions.findByIdForUpdate(anyLong())).thenAnswer(call -> Optional.ofNullable(em.find(TrainingSession.class, call.getArgument(0))));
        when(recordings.findByIdAndTrainingSessionIdAndTrainingSessionUserIdAndDeletedAtIsNull(anyLong(), anyLong(), anyLong()))
                .thenAnswer(call -> Optional.ofNullable(em.find(VoiceRecording.class, call.getArgument(0))));
        when(recordings.findByTrainingSessionIdAndTrainingSessionUserIdAndDeletedAtIsNullOrderByAttemptNoAsc(anyLong(), anyLong()))
                .thenAnswer(call -> em.createQuery("from VoiceRecording r where r.trainingSession.id = :session order by r.attemptNo", VoiceRecording.class)
                        .setParameter("session", sessionId).getResultList());
        doAnswer(call -> { em.flush(); return null; }).when(recordings).flush();
        writer = new VoiceRecordingWriterImpl(sessions, recordings, mock(RecordingDeletionScheduler.class), mock(RecordingMediaPreparationStore.class));
    }

    @AfterEach void close() {
        if (em != null) { if (em.getTransaction().isActive()) em.getTransaction().rollback(); em.close(); }
        if (factory != null) factory.close();
    }

    @Test void switchesSelectedRecordingWithoutViolatingTheActiveSelectionIndex() {
        select(secondId);
        assertSelection(secondId);
        select(firstId);
        assertSelection(firstId);
    }

    @Test void selectingTheSameRecordingIsIdempotent() {
        select(firstId);
        select(firstId);
        assertSelection(firstId);
    }

    private void select(Long id) {
        em.beginTransaction();
        writer.select(sessionId, id);
        em.getTransaction().commit();
        em.clear();
    }

    private void assertSelection(Long id) {
        var selected = em.createQuery("from VoiceRecording where selected = true", VoiceRecording.class).getResultList();
        assertThat(selected).extracting(VoiceRecording::getId).containsExactly(id);
    }

    private VoiceRecording recording(TrainingSession session, int attempt) {
        return VoiceRecording.create(session, attempt, "test/" + attempt, "audio/wav", 14000L, 7000,
                "a".repeat(64), RecordingQualityStatus.PASS, BigDecimal.ONE, BigDecimal.ZERO);
    }
}
