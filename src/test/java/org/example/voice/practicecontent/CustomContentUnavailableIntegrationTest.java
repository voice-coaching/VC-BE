package org.example.voice.practicecontent;

import org.example.voice.common.security.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="custom-content.encryption-key=") @AutoConfigureMockMvc
class CustomContentUnavailableIntegrationTest {
    @Autowired MockMvc mvc;
    @Test void missingServerKeyReturns503BeforeSaving() throws Exception {
        mvc.perform(post("/api/practice-contents/custom")
                .with(authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(1L),null,List.of())))
                .contentType("application/json").content("{\"title\":\"내 문장\",\"scriptText\":\"안녕하세요\",\"learningFocus\":\"BOTH\",\"retention\":\"SESSION_HISTORY\",\"locale\":\"ko-KR\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TEMPORARY_UNAVAILABLE"));
    }
}
