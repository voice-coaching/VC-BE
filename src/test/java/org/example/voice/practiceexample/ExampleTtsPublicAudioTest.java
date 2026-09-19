package org.example.voice.practiceexample;

import java.time.Clock;
import java.util.Optional;
import org.example.voice.practiceexample.application.*;
import org.example.voice.practiceexample.domain.PracticeExampleException;
import org.example.voice.practiceexample.domain.model.ExampleData.*;
import org.example.voice.practiceexample.domain.port.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExampleTtsPublicAudioTest {
    @Test void runpodCacheMissDoesNotGenerateOrUseGoogleCache() {
        var examples=mock(PracticeExampleService.class);
        var cache=mock(ExampleAudioCache.class);
        var synthesizer=mock(ExampleSpeechSynthesizer.class);
        var store=mock(ExampleTtsStore.class);
        var properties=new ExampleTtsProperties(); properties.setProvider("runpod");properties.setRevision("r1");
        when(examples.audioSource(1L,"example")).thenReturn(new Snapshot("example",1L,1,1L,1L,"text"));
        when(store.playable("example","r1")).thenReturn(Optional.empty());
        var service=new ExampleAudioService(examples,cache,synthesizer,Clock.systemUTC(),properties,store);
        assertThatThrownBy(()->service.audio(1L,"example",null)).isInstanceOf(PracticeExampleException.class);
        verifyNoInteractions(cache,synthesizer);
    }

    @Test void generatedAudioStillRequiresExampleAccessAndDoesNotSubstituteVoices() {
        var examples=mock(PracticeExampleService.class);
        var cache=mock(ExampleAudioCache.class);
        var synthesizer=mock(ExampleSpeechSynthesizer.class);
        var store=mock(ExampleTtsStore.class);
        var properties=new ExampleTtsProperties();properties.setProvider("runpod");properties.setRevision("r1");
        var audio=new Audio(new byte[]{73,68,51},"digest");
        when(examples.audioSource(1L,"example")).thenReturn(new Snapshot("example",1L,1,1L,1L,"text"));
        when(store.playable("example","r1")).thenReturn(Optional.of(audio));
        var service=new ExampleAudioService(examples,cache,synthesizer,Clock.systemUTC(),properties,store);
        assertThat(service.audio(1L,"example",null)).isSameAs(audio);
        verify(examples).audioSource(1L,"example");
        assertThatThrownBy(()->service.audio(1L,"example",ExampleAudioService.DEFAULT_VOICE)).isInstanceOf(PracticeExampleException.class);
        when(examples.audioSource(2L,"example")).thenThrow(PracticeExampleException.unavailable());
        assertThatThrownBy(()->service.audio(2L,"example",null)).isInstanceOf(PracticeExampleException.class);
        verify(store,times(1)).playable("example","r1");
        verifyNoInteractions(cache,synthesizer);
    }
}
