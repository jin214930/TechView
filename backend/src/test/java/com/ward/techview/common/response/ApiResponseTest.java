package com.ward.techview.common.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("성공 응답은 애플리케이션 코드 0과 HTTP 200을 가진다")
    void shouldCreateSuccessResponse() {
        ApiResponse<Void> response = ApiResponse.from(CommonResponseCode.SUCCESS);

        assertThat(response.code()).isZero();
        assertThat(response.httpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.message()).isEqualTo("요청에 성공했습니다.");
        assertThat(response.data()).isNull();
    }

    @Test
    @DisplayName("생성 응답은 애플리케이션 코드 0과 HTTP 201을 가진다")
    void shouldCreateCreatedResponse() {
        ApiResponse<Void> response = ApiResponse.from(CommonResponseCode.CREATED);

        assertThat(response.code()).isZero();
        assertThat(response.httpStatus()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("HTTP 상태와 null 데이터는 JSON 응답에서 제외된다")
    void shouldExcludeInternalStatusAndNullDataFromJson() throws Exception {
        ApiResponse<Void> response = ApiResponse.from(CommonResponseCode.SUCCESS);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.has("httpStatus")).isFalse();
        assertThat(json.has("data")).isFalse();
        assertThat(json.get("code").asInt()).isZero();
        assertThat(json.get("message").asText()).isEqualTo("요청에 성공했습니다.");
    }

    @Test
    @DisplayName("응답 데이터는 JSON의 data 필드에 포함된다")
    void shouldIncludeDataInJson() throws Exception {
        ApiResponse<TestData> response = ApiResponse.from(
                CommonResponseCode.SUCCESS,
                new TestData(1L, "TechView")
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.get("data").get("id").asLong()).isEqualTo(1L);
        assertThat(json.get("data").get("name").asText()).isEqualTo("TechView");
    }

    private record TestData(Long id, String name) {
    }
}
