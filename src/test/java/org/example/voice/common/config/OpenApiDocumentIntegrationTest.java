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
    void photoUploadDocumentsMediaTypeErrorsAndSuccessResponses() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode path = objectMapper.readTree(body).path("paths").path("/api/users/me/profile-image");
        assertThat(path.path("post").path("responses").has("201")).isTrue();
        assertThat(path.path("put").path("responses").has("200")).isTrue();
        for (String method : Set.of("post", "put")) {
            assertThat(path.path(method).path("responses").path("415").path("description").asText())
                    .contains("UNSUPPORTED_MEDIA_TYPE");
            assertThat(path.path(method).path("requestBody").path("content").has("multipart/form-data")).isTrue();
        }
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
        assertThat(operationCount).isEqualTo(90);
        paths.fieldNames().forEachRemaining(path -> assertThat(path).doesNotStartWith("/api/internal/"));
        assertThat(paths.path("/api/analysis-capabilities/canonical").path("get").path("summary").asText())
                .isEqualTo("Canonical 분석 접수 버전 조회");
        assertThat(paths.path("/api/analyses/{analysisId}/result-contract").path("get").path("summary").asText())
                .isEqualTo("현재 분석 결과 계약 조회");
        assertThat(paths.path("/api/analysis-capabilities").path("get").path("summary").asText())
                .isEqualTo("녹음·분석 지원 조건 조회");
    }
    @Test
    void paymentContractRequiresJwtExceptForSignedWebhookAndExposesTestMembershipFields() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var document = objectMapper.readTree(body);
        var paths = document.path("paths");
        var create = paths.path("/api/payment-orders").path("post");
        assertThat(create.path("security").toString()).contains("bearerAuth");
        assertThat(create.path("parameters").toString()).contains("Idempotency-Key", "\"required\":true");
        assertThat(paths.path("/api/payment-orders/{orderId}").has("get")).isTrue();
        assertThat(paths.path("/api/payment-orders/{orderId}/verify").has("post")).isTrue();
        var cancel = paths.path("/api/payment-orders/{orderId}/cancel").path("post");
        assertThat(cancel.path("security").toString()).contains("bearerAuth");
        assertThat(cancel.path("requestBody").path("required").asBoolean()).isTrue();
        assertThat(paths.path("/api/payment-orders/{orderId}/refund-requests").path("post")
                .path("security").toString()).contains("bearerAuth");
        assertThat(paths.path("/api/refund-requests/{requestId}").path("get")
                .path("security").toString()).contains("bearerAuth");
        assertThat(paths.path("/api/payments/webhooks/portone").path("post").path("security").isEmpty()).isTrue();
        var fields = document.path("components").path("schemas").path("UserProfileResponseDto").path("properties");
        assertThat(fields.has("membershipType")).isTrue();
        assertThat(fields.has("membershipExpiresAt")).isTrue();
        assertThat(fields.has("membershipEnvironment")).isTrue();
    }
    @Test
    void paymentHistoryDocumentsPaginationFieldsAndAuthentication() throws Exception {
        var body = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var document = objectMapper.readTree(body);
        var operation = document.path("paths").path("/api/users/me/payment-orders").path("get");
        assertThat(operation.path("security").toString()).contains("bearerAuth");
        assertThat(operation.path("parameters").toString()).contains("page", "size", "\"default\":20", "\"default\":0");
        for (var parameter : operation.path("parameters")) {
            assertThat(parameter.path("schema").path("type").asText()).isEqualTo("integer");
            assertThat(parameter.path("schema").path("format").asText()).isEqualTo("int32");
        }
        assertThat(operation.path("responses").path("400").path("description").asText()).contains("VALIDATION_ERROR");
        assertThat(operation.path("responses").has("401")).isTrue();
        assertThat(operation.path("responses").has("403")).isTrue();
        var schemas = document.path("components").path("schemas");
        var properties = schemas.path("PaymentHistoryResponse").path("properties");
        assertThat(properties.has("items")).isTrue();
        assertThat(properties.has("totalElements")).isTrue();
        assertThat(properties.has("totalPages")).isTrue();
        assertThat(properties.has("hasNext")).isTrue();
        assertThat(properties.path("items").path("items").path("$ref").asText()).endsWith("/PaymentHistoryItem");
        var itemFields = new java.util.HashSet<String>();
        schemas.path("PaymentHistoryItem").path("properties").fieldNames().forEachRemaining(itemFields::add);
        assertThat(itemFields).containsExactlyInAnyOrder("orderId", "product", "orderName", "totalAmount", "currency",
                "status", "environment", "createdAt", "paidAt", "expiresAt");
    }

    @Test
    void productionDefaultDoesNotExposeTestHistoryEvenToAuthenticatedUser() throws Exception {
        var authentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                new org.example.voice.common.security.LoginUser(1L), null, java.util.List.of());
        mockMvc.perform(get("/api/users/me/payment-orders").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(authentication)))
                .andExpect(status().isForbidden()).andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("TEST_PAYMENT_DISABLED"));
    }
}
