package com.ward.techview;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OpenApiIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("OpenAPI 문서에 JWT, Refresh Token 쿠키, SSE와 공통 오류 응답이 노출된다")
    void exposesApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("TechView API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/interviews/{sessionId}/events'].get.responses['200'].content['text/event-stream']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.parameters[?(@.name == 'refreshToken' && @.in == 'cookie')]").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/interviews'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/interviews'].post.responses", hasKey("400")))
                .andExpect(jsonPath("$.paths['/api/v1/interviews'].post.responses", hasKey("401")))
                .andExpect(jsonPath("$.paths['/api/v1/interviews'].post.responses", hasKey("409")))
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses", hasKey("201")))
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses", not(hasKey("200"))))
                .andExpect(jsonPath("$.paths['/api/v1/interviews'].post.responses", hasKey("201")))
                .andExpect(jsonPath("$.paths['/api/v1/interviews/{sessionId}/turns/{turnId}/answers'].post.responses", hasKey("201")))
                .andExpect(jsonPath("$.paths['/api/v1/answers/{answerId}/upload-completion'].post.responses", hasKey("202")))
                .andExpect(jsonPath("$.paths['/api/v1/answers/{answerId}/upload-completion'].post.responses", not(hasKey("200"))))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.code.type").value("integer"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.message.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.AnswerCreateRequest.required").value(hasItem("audioDurationMs")))
                .andExpect(jsonPath("$.components.schemas.ApiResponseEnvelope").exists())
                .andExpect(jsonPath("$.components.schemas.PageResponseEnvelope").exists());
    }

    @Test
    @DisplayName("Swagger UI 진입 경로가 인증 없이 제공된다")
    void exposesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
