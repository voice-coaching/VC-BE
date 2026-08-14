package org.example.voice.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiKoreanDocumentationTest {
    @Test
    void documentsEveryCurrentEndpointInKorean() {
        assertThat(OpenApiKoreanDocumentation.documentation()).hasSize(52);
        assertThat(OpenApiKoreanDocumentation.documentation().values())
                .allSatisfy(doc -> {
                    assertThat(doc.tag()).isNotBlank();
                    assertThat(doc.summary()).containsPattern("[가-힣]");
                    assertThat(doc.description()).containsPattern("[가-힣]");
                });
    }

    @Test
    void appliesDescriptionsSecurityAndSchemaExamples() {
        OpenAPI openApi = new OpenAPI().paths(new Paths()).components(new Components());
        OpenApiKoreanDocumentation.documentation().forEach((key, ignored) -> {
            PathItem pathItem = existing(openApi, key.path());
            pathItem.operation(key.method(), new Operation());
            openApi.getPaths().addPathItem(key.path(), pathItem);
        });
        ObjectSchema request = new ObjectSchema();
        request.addProperty("email", new StringSchema());
        openApi.getComponents().addSchemas("LoginRequestDto", request);

        OpenApiCustomizer customizer = new OpenApiKoreanDocumentation().koreanApiDocumentationCustomizer();
        customizer.customise(openApi);

        Operation publicOperation = openApi.getPaths().get("/api/auth/login").getPost();
        Operation securedOperation = openApi.getPaths().get("/api/users/me").getGet();
        assertThat(publicOperation.getSummary()).isEqualTo("이메일 로그인");
        assertThat(publicOperation.getSecurity()).isEmpty();
        assertThat(securedOperation.getSecurity()).extracting(Object::toString).anyMatch(value -> value.contains(OpenApiConfig.BEARER_AUTH));
        assertThat(request.getProperties().get("email").getDescription()).isEqualTo("사용자 이메일 주소");
        assertThat(request.getProperties().get("email").getExample()).isEqualTo("user@example.com");
    }

    private PathItem existing(OpenAPI openApi, String path) {
        PathItem existing = openApi.getPaths().get(path);
        return existing == null ? new PathItem() : existing;
    }
}
