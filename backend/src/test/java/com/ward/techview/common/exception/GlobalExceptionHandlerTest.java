package com.ward.techview.common.exception;

import com.ward.techview.common.response.ApiResponse;
import com.ward.techview.common.response.ApiResponseAdvice;
import com.ward.techview.common.response.CommonResponseCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResponseAdvice())
                .build();
    }

    @Test
    @DisplayName("정상 응답은 HTTP 200과 애플리케이션 코드 0을 반환한다")
    void shouldReturnSuccessResponse() throws Exception {
        mockMvc.perform(get("/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                .andExpect(jsonPath("$.data.value").value("success"))
                .andExpect(jsonPath("$.httpStatus").doesNotExist());
    }

    @Test
    @DisplayName("생성 응답은 HTTP 201과 애플리케이션 코드 0을 반환한다")
    void shouldReturnCreatedResponse() throws Exception {
        mockMvc.perform(post("/test/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TechView\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("비즈니스 예외는 지정된 HTTP 상태와 코드를 반환한다")
    void shouldHandleBusinessException() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("요청 본문 검증 실패는 첫 번째 검증 메시지를 반환한다")
    void shouldHandleRequestBodyValidation() throws Exception {
        mockMvc.perform(post("/test/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("이름은 필수입니다."));
    }

    @Test
    @DisplayName("잘못된 JSON 요청은 HTTP 400을 반환한다")
    void shouldHandleMalformedJson() throws Exception {
        mockMvc.perform(post("/test/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    @DisplayName("경로 변수 타입이 올바르지 않으면 HTTP 400을 반환한다")
    void shouldHandleTypeMismatch() throws Exception {
        mockMvc.perform(get("/test/numbers/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    @DisplayName("필수 요청 파라미터가 없으면 HTTP 400을 반환한다")
    void shouldHandleMissingRequestParameter() throws Exception {
        mockMvc.perform(get("/test/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    @DisplayName("리소스를 찾지 못하면 HTTP 404를 반환한다")
    void shouldHandleResourceNotFound() throws Exception {
        mockMvc.perform(get("/test/resource-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 HTTP 405를 반환한다")
    void shouldHandleMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/test/success"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    @DisplayName("지원하지 않는 미디어 타입은 HTTP 415를 반환한다")
    void shouldHandleUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/test/resources")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("TechView"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 내부 메시지 없이 HTTP 500을 반환한다")
    void shouldHandleUnexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(9000))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/success")
        ApiResponse<TestData> success() {
            return ApiResponse.from(CommonResponseCode.SUCCESS, new TestData("success"));
        }

        @PostMapping(value = "/test/resources", consumes = MediaType.APPLICATION_JSON_VALUE)
        ApiResponse<Void> create(@Valid @RequestBody TestRequest request) {
            return ApiResponse.from(CommonResponseCode.CREATED);
        }

        @GetMapping("/test/business-error")
        ApiResponse<Void> businessError() {
            throw new BusinessException(CommonResponseCode.FORBIDDEN);
        }

        @GetMapping("/test/numbers/{id}")
        ApiResponse<Long> number(@PathVariable Long id) {
            return ApiResponse.from(CommonResponseCode.SUCCESS, id);
        }

        @GetMapping("/test/required")
        ApiResponse<String> required(@RequestParam String value) {
            return ApiResponse.from(CommonResponseCode.SUCCESS, value);
        }

        @GetMapping("/test/resource-not-found")
        ApiResponse<Void> resourceNotFound() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing", "정적 리소스를 찾을 수 없습니다.");
        }

        @GetMapping("/test/unexpected")
        ApiResponse<Void> unexpected() {
            throw new IllegalStateException("외부에 노출하면 안 되는 내부 오류");
        }
    }

    record TestRequest(@NotBlank(message = "이름은 필수입니다.") String name) {
    }

    record TestData(String value) {
    }
}
