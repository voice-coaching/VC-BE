package org.example.voice.training.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.training.infrastructure.BrowserVideoGrantSigner;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** Public verification material only. AI pins this during deployment, not at request time. */
@RestController
@RequiredArgsConstructor
public class BrowserVideoPublicKeysController {
    private final BrowserVideoGrantSigner signer;

    @GetMapping("/api/browser-video/.well-known/upload-grant-keys")
    public ResponseEntity<Map<String,Object>> keys() {
        return ResponseEntity.ok().header("Cache-Control","public, max-age=300").body(signer.publicKeys());
    }
}
