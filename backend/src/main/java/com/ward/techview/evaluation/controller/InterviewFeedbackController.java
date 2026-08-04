package com.ward.techview.evaluation.controller;

import com.ward.techview.common.response.ApiResponse;
import com.ward.techview.common.response.CommonResponseCode;
import com.ward.techview.common.security.CustomUserDetails;
import com.ward.techview.evaluation.dto.InterviewFeedbackResponse;
import com.ward.techview.evaluation.service.InterviewFeedbackQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/interviews")
public class InterviewFeedbackController {
    private final InterviewFeedbackQueryService feedbackQueryService;

    @GetMapping("/{sessionId}/feedback")
    public ApiResponse<InterviewFeedbackResponse> getFeedback(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long sessionId
    ) {
        InterviewFeedbackResponse response = feedbackQueryService.getFeedback(principal.id(), sessionId);

        return ApiResponse.from(CommonResponseCode.SUCCESS, response);
    }
}
