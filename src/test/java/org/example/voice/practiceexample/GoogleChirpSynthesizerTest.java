package org.example.voice.practiceexample;

import org.example.voice.practiceexample.infrastructure.GoogleChirpSynthesizer;
import org.example.voice.practiceexample.domain.PracticeExampleException;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GoogleChirpSynthesizerTest {
    @Test void sendsOnlyServerTextAndExpectedChirpContract() {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        var adapter = new GoogleChirpSynthesizer(() -> "test-token", new ObjectMapper(), true, "test-project", builder.build());
        server.expect(requestTo("https://texttospeech.googleapis.com/v1/text:synthesize")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token")).andExpect(header("x-goog-user-project", "test-project"))
                .andExpect(content().json("{\"input\":{\"text\":\"게시 예문\"},\"voice\":{\"languageCode\":\"ko-KR\",\"name\":\"ko-KR-Chirp3-HD-Aoede\"},\"audioConfig\":{\"audioEncoding\":\"MP3\",\"speakingRate\":0.92}}"))
                .andRespond(withSuccess("{\"audioContent\":\"SUQz\"}", MediaType.APPLICATION_JSON));
        assertThat(adapter.synthesize("게시 예문", "ko-KR-Chirp3-HD-Aoede", 0.92)).containsExactly((byte)'I',(byte)'D',(byte)'3'); server.verify();
    }
    @Test void disabledNeverLoadsCredentialsAndFailuresAreSanitized() {
        var disabled = new GoogleChirpSynthesizer(() -> { throw new AssertionError("must not load ADC"); }, new ObjectMapper(), false, "", RestClient.create());
        assertThatThrownBy(() -> disabled.synthesize("text","voice",0.92)).isInstanceOf(PracticeExampleException.class);
        for (HttpStatus status : new HttpStatus[]{HttpStatus.TOO_MANY_REQUESTS,HttpStatus.FORBIDDEN,HttpStatus.INTERNAL_SERVER_ERROR}) {
            var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
            var adapter=new GoogleChirpSynthesizer(()->"test-token",new ObjectMapper(),true,"",builder.build());
            server.expect(requestTo("https://texttospeech.googleapis.com/v1/text:synthesize")).andRespond(withStatus(status).body("provider-private-error"));
            assertThatThrownBy(()->adapter.synthesize("text","voice",0.92)).isInstanceOfSatisfying(PracticeExampleException.class,e->{
                assertThat(e.status()).isEqualTo(status==HttpStatus.TOO_MANY_REQUESTS?429:503); assertThat(e.getMessage()).doesNotContain("provider-private-error");
            }); server.verify();
        }
    }
    @Test void rejectsMalformedMissingAndOversizedProviderPayloads() {
        for(String body:new String[]{"{}","{\"audioContent\":\"invalid!\"}","not-json"," ".repeat(2_800_001)}) {
            var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
            var adapter=new GoogleChirpSynthesizer(()->"test-token",new ObjectMapper(),true,"",builder.build());
            server.expect(requestTo("https://texttospeech.googleapis.com/v1/text:synthesize")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
            assertThatThrownBy(()->adapter.synthesize("text","voice",0.92)).isInstanceOfSatisfying(PracticeExampleException.class,e->assertThat(e.status()).isEqualTo(503));
        }
    }
}
