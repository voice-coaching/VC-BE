package org.example.voice.auth.infrastructure.sessioncache;

import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class SessionCacheOpenApi {
    @Bean OpenApiCustomizer sessionStoreErrors() {
        return api -> {
            for(String path:List.of("/api/auth/signup","/api/auth/login","/api/auth/social-login","/api/auth/token/refresh","/api/auth/logout","/api/users/me")) {
                var item=api.getPaths().get(path);
                if(item==null)continue;
                var operation=path.equals("/api/users/me")?item.getDelete():item.getPost();
                if(operation!=null)operation.getResponses().addApiResponse("503",new ApiResponse()
                        .description("AUTH_SESSION_STORE_UNAVAILABLE: 세션 무효화를 보장할 수 없어 변경하지 않았습니다. 잠시 후 다시 시도해 주세요.")
                        .addHeaderObject("Retry-After",new Header().schema(new StringSchema()).description("재시도 대기 시간(초)"))
                        .addHeaderObject("Cache-Control",new Header().schema(new StringSchema()).description("no-store")));
            }
        };
    }
}
