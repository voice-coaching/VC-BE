package org.example.voice.auth.infrastructure;

import tools.jackson.databind.JsonNode;
import org.example.voice.auth.domain.model.SocialUserInfo;
import org.example.voice.auth.domain.port.SocialOAuthProvider;
import org.example.voice.auth.domain.type.OAuthProvider;
import org.example.voice.auth.exception.AuthException;
import org.example.voice.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class NaverOAuthClient implements SocialOAuthProvider {
    private static final String TOKEN_URI = "https://nid.naver.com/oauth2.0/token";
    private static final String PROFILE_URI = "https://openapi.naver.com/v1/nid/me";
    private static final String SUCCESS_RESULT_CODE = "00";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    @Autowired
    public NaverOAuthClient(
            @Value("${auth.oauth.naver.client-id}") String clientId,
            @Value("${auth.oauth.naver.client-secret}") String clientSecret
    ) {
        this(RestClient.create(), clientId, clientSecret);
    }

    NaverOAuthClient(RestClient restClient, String clientId, String clientSecret) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.NAVER;
    }

    @Override
    public SocialUserInfo authenticate(String authorizationCode, String redirectUri, String state) {
        validateConfigurationAndState(state);
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("code", authorizationCode);
            form.add("state", state);
            form.add("redirect_uri", redirectUri);

            JsonNode token = restClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            String accessToken = required(token, "access_token");
            JsonNode profile = restClient.get()
                    .uri(PROFILE_URI)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(JsonNode.class);

            if (profile == null || !SUCCESS_RESULT_CODE.equals(profile.path("resultcode").asText())) {
                throw invalid();
            }
            JsonNode response = profile.path("response");
            return new SocialUserInfo(
                    required(response, "id"),
                    text(response, "email"),
                    text(response, "nickname")
            );
        } catch (AuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AuthException(ErrorCode.INVALID_AUTHORIZATION_CODE, exception);
        }
    }

    private void validateConfigurationAndState(String state) {
        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(state)) {
            throw invalid();
        }
    }

    private String required(JsonNode node, String field) {
        String value = text(node, field);
        if (isBlank(value)) {
            throw invalid();
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        return node == null || node.path(field).isMissingNode() ? null : node.path(field).asText(null);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private AuthException invalid() {
        return new AuthException(ErrorCode.INVALID_AUTHORIZATION_CODE);
    }
}
