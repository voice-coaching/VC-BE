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
public class KakaoOAuthClient implements SocialOAuthProvider {
    private final RestClient restClient = RestClient.create();
    @Value("${auth.oauth.kakao.client-id}") private String clientId;
    @Value("${auth.oauth.kakao.client-secret}") private String clientSecret;

    @Override public OAuthProvider provider() { return OAuthProvider.KAKAO; }

    @Override
    public SocialUserInfo authenticate(String authorizationCode, String redirectUri) {
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "authorization_code"); form.add("client_id", clientId);
            if (clientSecret != null && !clientSecret.isBlank()) form.add("client_secret", clientSecret);
            form.add("redirect_uri", redirectUri); form.add("code", authorizationCode);
            JsonNode token = restClient.post().uri("https://kauth.kakao.com/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
            String accessToken = required(token, "access_token");
            JsonNode user = restClient.get().uri("https://kapi.kakao.com/v2/user/me")
                    .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(JsonNode.class);
            String id = user == null ? null : user.path("id").asText(null);
            if (id == null) throw invalid();
            JsonNode account = user.path("kakao_account");
            String email = account.path("email").asText(null);
            String nickname = account.path("profile").path("nickname").asText(null);
            return new SocialUserInfo(id, email, nickname);
        } catch (AuthException exception) { throw exception; }
        catch (Exception exception) { throw new AuthException(ErrorCode.INVALID_AUTHORIZATION_CODE, exception); }
    }

    private String required(JsonNode node, String field) {
        String value = node == null ? null : node.path(field).asText(null); if (value == null || value.isBlank()) throw invalid(); return value;
    }
    private AuthException invalid() { return new AuthException(ErrorCode.INVALID_AUTHORIZATION_CODE); }
}
