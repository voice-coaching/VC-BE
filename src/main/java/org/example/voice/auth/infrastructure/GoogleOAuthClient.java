package org.example.voice.auth.infrastructure;

import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.model.SocialUserInfo;
import org.example.voice.auth.domain.port.SocialOAuthProvider;
import org.example.voice.auth.domain.type.OAuthProvider;
import org.example.voice.auth.exception.AuthException;
import org.example.voice.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class GoogleOAuthClient implements SocialOAuthProvider {
    private final RestClient restClient = RestClient.create();
    @Value("${auth.oauth.google.client-id}") private String clientId;
    @Value("${auth.oauth.google.client-secret}") private String clientSecret;

    @Override public OAuthProvider provider() { return OAuthProvider.GOOGLE; }

    @Override
    public SocialUserInfo authenticate(String authorizationCode, String redirectUri) {
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("code", authorizationCode); form.add("client_id", clientId); form.add("client_secret", clientSecret);
            form.add("redirect_uri", redirectUri); form.add("grant_type", "authorization_code");
            JsonNode token = restClient.post().uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
            String accessToken = required(token, "access_token");
            JsonNode user = restClient.get().uri("https://openidconnect.googleapis.com/v1/userinfo")
                    .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(JsonNode.class);
            if (user == null || !user.path("email_verified").asBoolean(false)) throw invalid();
            return new SocialUserInfo(required(user, "sub"), text(user, "email"), text(user, "name"));
        } catch (AuthException exception) { throw exception; }
        catch (Exception exception) { throw new AuthException(ErrorCode.INVALID_AUTHORIZATION_CODE, exception); }
    }

    private String required(JsonNode node, String field) {
        String value = text(node, field); if (value == null || value.isBlank()) throw invalid(); return value;
    }
    private String text(JsonNode node, String field) { return node == null || node.path(field).isMissingNode() ? null : node.path(field).asText(null); }
    private AuthException invalid() { return new AuthException(ErrorCode.INVALID_AUTHORIZATION_CODE); }
}
