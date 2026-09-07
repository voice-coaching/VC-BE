package org.example.voice.auth.infrastructure;

import org.example.voice.auth.exception.AuthException;
import org.example.voice.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NaverOAuthClientTest {
    private MockRestServiceServer server;
    private NaverOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NaverOAuthClient(builder.build(), "client-id", "client-secret");
    }

    @Test
    void exchangesAuthorizationCodeAndReturnsNaverProfile() {
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("client_id=client-id")))
                .andExpect(content().string(containsString("client_secret=client-secret")))
                .andExpect(content().string(containsString("code=authorization-code")))
                .andExpect(content().string(containsString("state=state-value")))
                .andRespond(withSuccess("""
                        {"access_token":"naver-access-token","token_type":"bearer","expires_in":"3600"}
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer naver-access-token"))
                .andRespond(withSuccess("""
                        {
                          "resultcode":"00",
                          "message":"success",
                          "response":{"id":"naver-user-id","email":"user@naver.com","nickname":"네이버사용자"}
                        }
                        """, MediaType.APPLICATION_JSON));

        var profile = client.authenticate(
                "authorization-code",
                "https://app.example.com/oauth/naver/callback",
                "state-value"
        );

        assertThat(profile.providerUserId()).isEqualTo("naver-user-id");
        assertThat(profile.email()).isEqualTo("user@naver.com");
        assertThat(profile.nickname()).isEqualTo("네이버사용자");
        server.verify();
    }

    @Test
    void rejectsMissingStateWithoutCallingNaver() {
        assertThatThrownBy(() -> client.authenticate("code", "redirect", " "))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_AUTHORIZATION_CODE));
        server.verify();
    }

    @Test
    void rejectsProfileResponseWithoutRequiredProviderUserId() {
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
                .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andRespond(withSuccess("""
                        {"resultcode":"00","message":"success","response":{"email":"user@naver.com"}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.authenticate("code", "redirect", "state"))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_AUTHORIZATION_CODE));
        server.verify();
    }
}
