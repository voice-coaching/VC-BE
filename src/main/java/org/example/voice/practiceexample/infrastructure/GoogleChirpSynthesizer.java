package org.example.voice.practiceexample.infrastructure;

import org.example.voice.practiceexample.domain.PracticeExampleException;
import org.example.voice.practiceexample.domain.port.ExampleSpeechSynthesizer;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Component
public class GoogleChirpSynthesizer implements ExampleSpeechSynthesizer {
    private static final String URL = "https://texttospeech.googleapis.com/v1/text:synthesize";
    private final GoogleTtsTokenProvider tokens;
    private final ObjectMapper json;
    private final boolean enabled;
    private final String project;
    private final RestClient http;
    @Autowired
    public GoogleChirpSynthesizer(GoogleTtsTokenProvider tokens, ObjectMapper json,
            @Value("${practice-examples.tts.enabled:false}") boolean enabled,
            @Value("${practice-examples.tts.quota-project:}") String project) {
        this(tokens, json, enabled, project, client());
    }
    public GoogleChirpSynthesizer(GoogleTtsTokenProvider tokens, ObjectMapper json, boolean enabled, String project, RestClient client) {
        this.tokens = tokens; this.json = json; this.enabled = enabled; this.project = project; this.http = client;
    }
    private static RestClient client() {
        var factory = new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(5000); factory.setReadTimeout(30000);
        return RestClient.builder().requestFactory(factory).build();
    }
    public byte[] synthesize(String text, String voice, double speakingRate) {

        try {
            var body = Map.of("input", Map.of("text", text), "voice", Map.of("languageCode", "ko-KR", "name", voice),
                    "audioConfig", Map.of("audioEncoding", "MP3", "speakingRate", speakingRate));
            var request = http.post().uri(URL).contentType(MediaType.APPLICATION_JSON).headers(headers -> {
                headers.setBearerAuth(tokens.token());
                if (!project.isBlank()) headers.set("x-goog-user-project", project);
            }).body(body);
            return request.exchange((req, response) -> {

                byte[] payload = response.getBody().readAllBytes();

                var content = json.readTree(payload).path("audioContent");

                return Base64.getDecoder().decode(content.asText());
            });
        } catch (PracticeExampleException e) { throw e; }
        catch (Exception e) { throw PracticeExampleException.unavailable(); }
    }
}
