package org.example.voice.auth.infrastructure;

import org.example.voice.auth.domain.port.SocialOAuthProvider;
import org.example.voice.auth.exception.AuthException;
import org.example.voice.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OAuthProviderValidationTest {
    @Test void googleAcceptsVerifiedProfilesAndRejectsUnverifiedEmailOrMissingSubject() {
        var valid=fixture(true);
        respond(valid,"{\"access_token\":\"fixture-token\"}","{\"sub\":\"subject\",\"email_verified\":true,\"email\":\"user@example.invalid\"}");
        assertThat(valid.client.authenticate("code","redirect",null).providerUserId()).isEqualTo("subject");valid.server.verify();
        for(String body:new String[]{"{\"sub\":\"subject\",\"email_verified\":false}","{\"email_verified\":true}"}) {
            var invalid=fixture(true);respond(invalid,"{\"access_token\":\"fixture-token\"}",body);reject(invalid);invalid.server.verify();
        }
    }

    @Test void kakaoAcceptsIdentifiedProfilesAndRejectsMissingProviderId() {
        var valid=fixture(false);respond(valid,"{\"access_token\":\"fixture-token\"}","{\"id\":123,\"kakao_account\":{\"profile\":{\"nickname\":\"fixture\"}}}");
        assertThat(valid.client.authenticate("code","redirect",null).providerUserId()).isEqualTo("123");valid.server.verify();
        var invalid=fixture(false);respond(invalid,"{\"access_token\":\"fixture-token\"}","{\"kakao_account\":{}}");reject(invalid);invalid.server.verify();
    }

    @Test void missingAccessTokensNeverTriggerAProfileRequest() {
        for(boolean google:new boolean[]{true,false}) {
            var invalid=fixture(google);invalid.server.expect(requestTo(invalid.tokenUrl)).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
            reject(invalid);invalid.server.verify();
        }
    }

    private void reject(Fixture fixture){
        assertThatThrownBy(()->fixture.client.authenticate("code","redirect",null)).isInstanceOfSatisfying(AuthException.class,
                error->assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_AUTHORIZATION_CODE));
    }
    private void respond(Fixture fixture,String token,String profile){
        fixture.server.expect(requestTo(fixture.tokenUrl)).andRespond(withSuccess(token,MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(fixture.profileUrl)).andRespond(withSuccess(profile,MediaType.APPLICATION_JSON));
    }
    private Fixture fixture(boolean google){
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        SocialOAuthProvider client=google?new GoogleOAuthClient():new KakaoOAuthClient();
        ReflectionTestUtils.setField(client,"restClient",builder.build());
        ReflectionTestUtils.setField(client,"clientId","fixture-client");ReflectionTestUtils.setField(client,"clientSecret","fixture-secret");
        return new Fixture(client,server,google?"https://oauth2.googleapis.com/token":"https://kauth.kakao.com/oauth/token",
                google?"https://openidconnect.googleapis.com/v1/userinfo":"https://kapi.kakao.com/v2/user/me");
    }
    private record Fixture(SocialOAuthProvider client,MockRestServiceServer server,String tokenUrl,String profileUrl){}
}
