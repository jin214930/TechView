package com.ward.techview.voice.controller;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.exception.GlobalExceptionHandler;
import com.ward.techview.common.response.ApiResponseAdvice;
import com.ward.techview.common.security.CustomUserDetails;
import com.ward.techview.voice.dto.AnswerCreateRequest;
import com.ward.techview.voice.dto.AnswerCreateResponse;
import com.ward.techview.voice.response.VoiceResponseCode;
import com.ward.techview.voice.service.AnswerUploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class AnswerUploadControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long TURN_ID = 100L;
    private static final Long ANSWER_ID = 1000L;
    private static final String ENDPOINT =
            "/api/v1/interviews/{sessionId}/turns/{turnId}/answers";
    private static final CustomUserDetails PRINCIPAL =
            new CustomUserDetails(MEMBER_ID, "user@example.com");

    @Mock
    private AnswerUploadService answerUploadService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of())
        );

        mockMvc = standaloneSetup(new AnswerUploadController(answerUploadService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResponseAdvice())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("답변을 생성하면 HTTP 201과 Presigned URL을 반환한다")
    void shouldReturnCreatedWithPresignedUploadUrl() throws Exception {
        AnswerCreateRequest request = new AnswerCreateRequest("audio/webm", 42_000);
        AnswerCreateResponse response = new AnswerCreateResponse(
                ANSWER_ID,
                true,
                URI.create("http://localhost:9000/techview-audio/upload"),
                OffsetDateTime.of(2026, 7, 31, 12, 5, 0, 0, ZoneOffset.UTC)
        );
        when(answerUploadService.createUpload(MEMBER_ID, SESSION_ID, TURN_ID, request))
                .thenReturn(response);

        mockMvc.perform(post(ENDPOINT, SESSION_ID, TURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "audioContentType": "audio/webm",
                                  "audioDurationMs": 42000
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.answerId").value(ANSWER_ID))
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.uploadUrl")
                        .value("http://localhost:9000/techview-audio/upload"))
                .andExpect(jsonPath("$.data.uploadExpiresAt").exists());

        verify(answerUploadService).createUpload(MEMBER_ID, SESSION_ID, TURN_ID, request);
    }

    @Test
    @DisplayName("기존 답변의 업로드 URL을 재발급하면 HTTP 200을 반환한다")
    void shouldReturnOkWhenUploadUrlIsReissued() throws Exception {
        AnswerCreateRequest request = new AnswerCreateRequest("audio/webm", 42_000);
        AnswerCreateResponse response = new AnswerCreateResponse(
                ANSWER_ID,
                false,
                URI.create("http://localhost:9000/techview-audio/upload-reissued"),
                OffsetDateTime.of(2026, 7, 31, 12, 10, 0, 0, ZoneOffset.UTC)
        );
        when(answerUploadService.createUpload(MEMBER_ID, SESSION_ID, TURN_ID, request))
                .thenReturn(response);

        mockMvc.perform(post(ENDPOINT, SESSION_ID, TURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "audioContentType": "audio/webm",
                                  "audioDurationMs": 42000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.answerId").value(ANSWER_ID))
                .andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.uploadUrl")
                        .value("http://localhost:9000/techview-audio/upload-reissued"));
    }

    @Test
    @DisplayName("음성 Content-Type이 비어 있으면 HTTP 400을 반환한다")
    void shouldRejectBlankAudioContentType() throws Exception {
        mockMvc.perform(post(ENDPOINT, SESSION_ID, TURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "audioContentType": " ",
                                  "audioDurationMs": 42000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        verify(answerUploadService, never()).createUpload(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("음성 재생 시간이 양수가 아니면 HTTP 400을 반환한다")
    void shouldRejectNonPositiveAudioDuration() throws Exception {
        mockMvc.perform(post(ENDPOINT, SESSION_ID, TURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "audioContentType": "audio/webm",
                                  "audioDurationMs": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        verify(answerUploadService, never()).createUpload(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("지원하지 않는 음성 형식이면 HTTP 415를 반환한다")
    void shouldReturnUnsupportedMediaTypeForUnsupportedAudioFormat() throws Exception {
        AnswerCreateRequest request = new AnswerCreateRequest("audio/wav", 42_000);
        when(answerUploadService.createUpload(MEMBER_ID, SESSION_ID, TURN_ID, request))
                .thenThrow(new BusinessException(VoiceResponseCode.UNSUPPORTED_AUDIO_CONTENT_TYPE));

        mockMvc.perform(post(ENDPOINT, SESSION_ID, TURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "audioContentType": "audio/wav",
                                  "audioDurationMs": 42000
                                }
                                """))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(4000));
    }
}
