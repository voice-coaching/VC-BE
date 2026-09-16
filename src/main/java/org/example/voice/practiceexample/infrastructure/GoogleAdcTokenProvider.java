package org.example.voice.practiceexample.infrastructure;

import com.google.auth.oauth2.GoogleCredentials;
import org.example.voice.practiceexample.domain.PracticeExampleException;
import org.springframework.stereotype.Component;

@Component
public class GoogleAdcTokenProvider implements GoogleTtsTokenProvider {
    private GoogleCredentials credentials;
    public synchronized String token() {
        try {
            if (credentials == null) credentials = GoogleCredentials.getApplicationDefault().createScoped("https://www.googleapis.com/auth/cloud-platform");
            credentials.refreshIfExpired();
            var token = credentials.getAccessToken();
            if (token == null || token.getTokenValue().isBlank()) throw PracticeExampleException.unavailable();
            return token.getTokenValue();
        } catch (Exception e) { throw PracticeExampleException.unavailable(); }
    }
}
