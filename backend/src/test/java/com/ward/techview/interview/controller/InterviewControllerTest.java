package com.ward.techview.interview.controller;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.exception.GlobalExceptionHandler;
import com.ward.techview.common.response.ApiResponseAdvice;
import com.ward.techview.common.security.CustomUserDetails;
import com.ward.techview.interview.dto.ActiveInterviewResponse;
import com.ward.techview.interview.dto.InterviewStartResponse;
import com.ward.techview.interview.entity.InterviewTurnType;
import com.ward.techview.interview.response.InterviewResponseCode;
import com.ward.techview.interview.service.InterviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class InterviewControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long TURN_ID = 100L;
    private static final CustomUserDetails PRINCIPAL =
            new CustomUserDetails(MEMBER_ID, "user@example.com");

    @Mock
    private InterviewService interviewService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of())
        );

        mockMvc = standaloneSetup(new InterviewController(interviewService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResponseAdvice())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("진행 중인 면접이 있으면 세션 요약을 반환한다")
    void shouldReturnActiveInterviewSummary() throws Exception {
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        ActiveInterviewResponse response = new ActiveInterviewResponse(
                true,
                SESSION_ID,
                startedAt,
                2
        );
        when(interviewService.getActiveSession(MEMBER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/interviews/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                .andExpect(jsonPath("$.data.hasActiveSession").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.data.currentTurnOrder").value(2));

        verify(interviewService).getActiveSession(MEMBER_ID);
    }

    @Test
    @DisplayName("진행 중인 면접이 없으면 비활성 상태를 반환한다")
    void shouldReturnInactiveInterviewSummary() throws Exception {
        when(interviewService.getActiveSession(MEMBER_ID))
                .thenReturn(ActiveInterviewResponse.none());

        mockMvc.perform(get("/api/v1/interviews/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasActiveSession").value(false))
                .andExpect(jsonPath("$.data.sessionId").doesNotExist())
                .andExpect(jsonPath("$.data.startedAt").doesNotExist())
                .andExpect(jsonPath("$.data.currentTurnOrder").doesNotExist());
    }

    @Test
    @DisplayName("새 면접을 시작하면 HTTP 201과 첫 번째 질문을 반환한다")
    void shouldReturnCreatedWhenStartingNewInterview() throws Exception {
        InterviewStartResponse response = startResponse(false);
        when(interviewService.startOrResume(MEMBER_ID)).thenReturn(response);

        mockMvc.perform(post("/api/v1/interviews"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("리소스가 생성되었습니다."))
                .andExpect(jsonPath("$.data.resumed").value(false))
                .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.data.turnId").value(TURN_ID))
                .andExpect(jsonPath("$.data.turnOrder").value(1))
                .andExpect(jsonPath("$.data.turnType").value("CORE"))
                .andExpect(jsonPath("$.data.question").value("첫 번째 질문"));

        verify(interviewService).startOrResume(MEMBER_ID);
    }

    @Test
    @DisplayName("기존 면접을 재개하면 HTTP 200과 현재 질문을 반환한다")
    void shouldReturnOkWhenResumingInterview() throws Exception {
        InterviewStartResponse response = startResponse(true);
        when(interviewService.startOrResume(MEMBER_ID)).thenReturn(response);

        mockMvc.perform(post("/api/v1/interviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                .andExpect(jsonPath("$.data.resumed").value(true))
                .andExpect(jsonPath("$.data.turnId").value(TURN_ID));
    }

    @Test
    @DisplayName("현재 면접 상태가 올바르지 않으면 HTTP 500을 반환한다")
    void shouldReturnInternalServerErrorWhenInterviewStateIsInvalid() throws Exception {
        when(interviewService.startOrResume(MEMBER_ID))
                .thenThrow(new BusinessException(InterviewResponseCode.INTERVIEW_STATE_INVALID));

        mockMvc.perform(post("/api/v1/interviews"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(3000))
                .andExpect(jsonPath("$.message").value("면접 진행 상태가 올바르지 않습니다."));
    }

    private static InterviewStartResponse startResponse(boolean resumed) {
        return new InterviewStartResponse(
                resumed,
                SESSION_ID,
                TURN_ID,
                1,
                InterviewTurnType.CORE,
                "첫 번째 질문"
        );
    }
}
