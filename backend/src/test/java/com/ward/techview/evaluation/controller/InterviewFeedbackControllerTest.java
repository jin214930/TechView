package com.ward.techview.evaluation.controller;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.exception.GlobalExceptionHandler;
import com.ward.techview.common.response.ApiResponseAdvice;
import com.ward.techview.common.security.CustomUserDetails;
import com.ward.techview.evaluation.dto.InterviewFeedbackResponse;
import com.ward.techview.evaluation.response.EvaluationResponseCode;
import com.ward.techview.evaluation.service.InterviewFeedbackQueryService;
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

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class InterviewFeedbackControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final CustomUserDetails PRINCIPAL =
            new CustomUserDetails(MEMBER_ID, "user@example.com");

    @Mock
    private InterviewFeedbackQueryService feedbackQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of())
        );

        mockMvc = standaloneSetup(
                new InterviewFeedbackController(feedbackQueryService)
        )
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResponseAdvice())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("본인 면접의 종합 피드백 생성 상태를 조회한다")
    void returnsInterviewFeedbackStatus() throws Exception {
        when(feedbackQueryService.getFeedback(MEMBER_ID, SESSION_ID))
                .thenReturn(InterviewFeedbackResponse.processing(SESSION_ID));

        mockMvc.perform(get("/api/v1/interviews/{sessionId}/feedback", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.feedback").doesNotExist())
                .andExpect(jsonPath("$.data.errorMessage").doesNotExist());

        verify(feedbackQueryService).getFeedback(MEMBER_ID, SESSION_ID);
    }

    @Test
    @DisplayName("피드백 처리 상태가 비정상이면 HTTP 500을 반환한다")
    void returnsInternalServerErrorWhenFeedbackStateIsInvalid() throws Exception {
        when(feedbackQueryService.getFeedback(MEMBER_ID, SESSION_ID))
                .thenThrow(new BusinessException(
                        EvaluationResponseCode.INTERVIEW_FEEDBACK_STATE_INVALID
                ));

        mockMvc.perform(get("/api/v1/interviews/{sessionId}/feedback", SESSION_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(5000));
    }
}
