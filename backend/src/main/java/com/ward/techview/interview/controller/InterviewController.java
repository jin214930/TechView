package com.ward.techview.interview.controller;

import com.ward.techview.common.response.ApiResponse;
import com.ward.techview.common.response.CommonResponseCode;
import com.ward.techview.common.security.CustomUserDetails;
import com.ward.techview.interview.dto.ActiveInterviewResponse;
import com.ward.techview.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/interviews")
public class InterviewController {
    private final InterviewService interviewService;

    @GetMapping("/active")
    public ApiResponse<ActiveInterviewResponse> getActiveSession(@AuthenticationPrincipal CustomUserDetails principal) {
        ActiveInterviewResponse response = interviewService.getActiveSession(principal.id());
        return ApiResponse.from(CommonResponseCode.SUCCESS, response);
    }
}
