package org.example.voice.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentIntegrationTest {
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generatedOpenApiContainsKoreanDocumentationForEveryEndpoint() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(body).path("paths");
        int operationCount = 0;
        for (JsonNode path : paths) {
            var fields = path.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (!HTTP_METHODS.contains(field.getKey())) continue;
                operationCount++;
                assertThat(field.getValue().path("summary").asText()).containsPattern("[가-힣]");
                assertThat(field.getValue().path("description").asText()).containsPattern("[가-힣]");
                assertThat(field.getValue().path("tags").get(0).asText()).containsPattern("[가-힣]");
            }
        }
        assertThat(operationCount).isEqualTo(53);
    }
}
