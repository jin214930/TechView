package com.ward.techview.common.openapi;

import com.ward.techview.common.response.ApiResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI techViewOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TechView API")
                        .version("v1")
                        .description("AI 모의 기술 면접 서비스 API"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSchemas("ApiResponseEnvelope", apiResponseSchema())
                        .addSchemas("PageResponseEnvelope", pageResponseSchema())
                        .addSchemas("ApiErrorResponse", errorSchema())
                        .addResponses("CommonSuccessResponse", reusableResponse(
                                "공통 성공 응답",
                                "#/components/schemas/ApiResponseEnvelope"
                        ))
                        .addResponses("CommonPageResponse", reusableResponse(
                                "공통 페이지 응답",
                                "#/components/schemas/PageResponseEnvelope"
                        )));
    }

    @Bean
    OperationCustomizer commonErrorResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            addResponseIfAbsent(responses, "400", "요청 형식 또는 입력값 검증 실패");
            addResponseIfAbsent(responses, "401", "인증 실패");
            addResponseIfAbsent(responses, "403", "접근 권한 없음");
            addResponseIfAbsent(responses, "409", "도메인 상태 충돌");
            addResponseIfAbsent(responses, "500", "서버 내부 오류");
            return operation;
        };
    }

    private void addResponseIfAbsent(ApiResponses responses, String status, String description) {
        if (!responses.containsKey(status)) {
            responses.addApiResponse(status, new io.swagger.v3.oas.models.responses.ApiResponse()
                    .description(description)
                    .content(new Content().addMediaType(
                            org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))
                    )));
        }
    }

    private Schema<?> errorSchema() {
        return new Schema<ApiResponse<Void>>()
                .type("object")
                .addProperty("code", new Schema<Integer>().type("integer").example(1000))
                .addProperty("message", new Schema<String>().type("string").example("요청을 처리할 수 없습니다."));
    }

    private Schema<?> apiResponseSchema() {
        return new Schema<>()
                .type("object")
                .description("모든 JSON API가 사용하는 공통 응답 봉투")
                .addProperty("code", new Schema<Integer>().type("integer").example(0))
                .addProperty("message", new Schema<String>().type("string").example("요청에 성공했습니다."))
                .addProperty("data", new Schema<>().description("엔드포인트별 응답 데이터"));
    }

    private Schema<?> pageResponseSchema() {
        return new Schema<>()
                .type("object")
                .description("페이지 기반 목록 공통 응답")
                .addProperty("items", new ArraySchema().items(new Schema<>()))
                .addProperty("page", new Schema<Integer>().type("integer"))
                .addProperty("size", new Schema<Integer>().type("integer"))
                .addProperty("totalElements", new Schema<Long>().type("integer").format("int64"))
                .addProperty("totalPages", new Schema<Integer>().type("integer"))
                .addProperty("hasNext", new Schema<Boolean>().type("boolean"));
    }

    private io.swagger.v3.oas.models.responses.ApiResponse reusableResponse(String description, String schemaReference) {
        return new io.swagger.v3.oas.models.responses.ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref(schemaReference))
                ));
    }
}
