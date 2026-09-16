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
    void documentsSupportValidationIdempotencyAndErrorCodes() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode document = objectMapper.readTree(body);
        JsonNode operation = document.path("paths").path("/api/inquiries").path("post");
        assertThat(operation.path("responses").has("201")).isTrue();
        assertThat(operation.path("responses").path("409").path("description").asText()).contains("CONFLICT");
        assertThat(operation.path("parameters").toString()).contains("Idempotency-Key");
        String schemaRef = operation.path("requestBody").path("content").path("application/json")
                .path("schema").path("$ref").asText();
        JsonNode request = document.path("components").path("schemas")
                .path(schemaRef.substring(schemaRef.lastIndexOf('/') + 1));
        assertThat(request.path("properties").path("subject").path("maxLength").asInt()).isEqualTo(100);
        assertThat(request.path("properties").path("body").path("maxLength").asInt()).isEqualTo(2000);
        assertThat(request.path("required").toString()).contains("category", "subject", "body");
    }

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
        assertThat(operationCount).isEqualTo(71);
        assertThat(paths.path("/api/analysis-capabilities").path("get").path("summary").asText())
                .isEqualTo("녹음·분석 지원 조건 조회");
    }
}
