package org.example.voice.practiceexample.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.practiceexample.application.*;
import org.example.voice.practiceexample.domain.PracticeExampleException;
import org.example.voice.practiceexample.domain.model.ExampleData.Examples;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import java.util.Map;

@RestController @RequiredArgsConstructor @RequestMapping("/api")
public class PracticeExampleController {
    private final PracticeExampleService examples;
    private final ExampleAudioService audio;
    @GetMapping("/courses/{courseId}/steps/{stepId}/practice-examples")
    public ApiResponse<Examples> list(@AuthenticationPrincipal LoginUser user, @PathVariable Long courseId,
                                      @PathVariable Long stepId, @RequestParam(required = false) Long sessionId) {
        return ApiResponse.success("OK", examples.list(user.id(), courseId, stepId, sessionId));
    }
    @GetMapping("/practice-examples/{exampleId}/audio")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "MP3 audio", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "audio/mpeg", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "304", description = "ETag 일치", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "TTS_UNAVAILABLE", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "TTS_RATE_LIMITED", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json"))
    })
    public ResponseEntity<byte[]> audio(@AuthenticationPrincipal LoginUser user, @PathVariable String exampleId,
            @RequestParam(defaultValue = ExampleAudioService.DEFAULT_VOICE) String voice,
            @RequestParam Map<String, String> query, WebRequest request) {
        if (query.keySet().stream().anyMatch(key -> !key.equals("voice"))) throw PracticeExampleException.invalid();
        var data = audio.audio(user.id(), exampleId, voice);
        var headers = new HttpHeaders(); headers.setETag(data.etag()); headers.setCacheControl("private, max-age=86400");
        headers.setVary(java.util.List.of("Authorization")); headers.set("X-Content-Type-Options", "nosniff");
        if (request.checkNotModified(data.etag())) return new ResponseEntity<>(null, headers, HttpStatus.NOT_MODIFIED);
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        return new ResponseEntity<>(data.bytes(), headers, HttpStatus.OK);
    }
}
