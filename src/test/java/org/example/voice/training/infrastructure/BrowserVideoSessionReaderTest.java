package org.example.voice.training.infrastructure;

import org.example.voice.common.exception.BaseException;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrowserVideoSessionReaderTest {
    @Test void compatibilityRepositoryCannotBypassOwnership() {
        var repo=mock(TrainingSessionJpaRepository.class);
        var session=mock(TrainingSession.class);
        when(repo.findById(12L)).thenReturn(Optional.of(session));
        when(session.getUserId()).thenReturn(99L);
        assertThrows(BaseException.class,()->new BrowserVideoSessionReaderImpl(repo).ownedSource(12L,7L));
        verify(session,never()).analysisScriptText();
    }

    @Test void ownedActiveSessionProvidesServerSelectedSentence() {
        var repo=mock(TrainingSessionJpaRepository.class);
        var session=mock(TrainingSession.class);var content=mock(PracticeContent.class);
        when(repo.findById(12L)).thenReturn(Optional.of(session));
        when(session.getUserId()).thenReturn(7L);when(session.getStatus()).thenReturn(TrainingSessionStatus.RECORDING);
        when(session.getLearningFocus()).thenReturn(LearningFocus.PRONUNCIATION);
        when(session.getContent()).thenReturn(content);when(content.getId()).thenReturn(11L);
        when(session.analysisScriptText()).thenReturn("서버가 선택한 문장");
        var result=new BrowserVideoSessionReaderImpl(repo).ownedSource(12L,7L);
        assertEquals("서버가 선택한 문장",result.scriptText());assertEquals(11L,result.contentId());
        when(session.getStatus()).thenReturn(TrainingSessionStatus.CANCELED);
        assertThrows(BaseException.class,()->new BrowserVideoSessionReaderImpl(repo).ownedSource(12L,7L));
    }
}
