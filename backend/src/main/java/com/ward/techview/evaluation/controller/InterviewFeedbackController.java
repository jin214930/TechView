package com.ward.techview.evaluation.controller;

import com.ward.techview.common.response.ApiResponse;
import com.ward.techview.common.response.CommonResponseCode;
import com.ward.techview.common.response.PageResponse;
import com.ward.techview.common.security.CustomUserDetails;
import com.ward.techview.evaluation.dto.InterviewFeedbackResponse;
import com.ward.techview.evaluation.dto.InterviewHistoryItemResponse;
import com.ward.techview.evaluation.service.InterviewFeedbackHistoryQueryService;
import com.ward.techview.evaluation.service.InterviewFeedbackQueryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/interviews")
@SecurityRequirement(name = "bearerAuth")
public class InterviewFeedbackController {
    private final InterviewFeedbackQueryService feedbackQueryService;
    private final InterviewFeedbackHistoryQueryService feedbackHistoryQueryService;

    @GetMapping("/{sessionId}/feedback")
    public ApiResponse<InterviewFeedbackResponse> getFeedback(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long sessionId
    ) {
        InterviewFeedbackResponse response = feedbackQueryService.getFeedback(principal.id(), sessionId);

        return ApiResponse.from(CommonResponseCode.SUCCESS, response);
    }

    @GetMapping
    public ApiResponse<PageResponse<InterviewHistoryItemResponse>>
    getMyCompletedInterviews(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<InterviewHistoryItemResponse> response = feedbackHistoryQueryService.getCompletedInterviews(principal.id(), page, size);

        return ApiResponse.from(CommonResponseCode.SUCCESS, response);
    }
}
