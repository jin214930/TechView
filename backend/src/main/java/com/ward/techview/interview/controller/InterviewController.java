package com.ward.techview.interview.controller;

import com.ward.techview.common.response.ApiResponse;
import com.ward.techview.common.response.CommonResponseCode;
import com.ward.techview.common.security.CustomUserDetails;
import com.ward.techview.interview.dto.ActiveInterviewResponse;
import com.ward.techview.interview.dto.InterviewProgressResponse;
import com.ward.techview.interview.dto.InterviewStartResponse;
import com.ward.techview.interview.service.InterviewProgressQueryService;
import com.ward.techview.interview.service.InterviewProgressSseService;
import com.ward.techview.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/interviews")
public class InterviewController {
    private final InterviewService interviewService;
    private final InterviewProgressQueryService interviewProgressQueryService;
    private final InterviewProgressSseService interviewProgressSseService;

    @GetMapping("/active")
    public ApiResponse<ActiveInterviewResponse> getActiveSession(@AuthenticationPrincipal CustomUserDetails principal) {
        ActiveInterviewResponse response = interviewService.getActiveSession(principal.id());
        return ApiResponse.from(CommonResponseCode.SUCCESS, response);
    }

    @PostMapping
    public ApiResponse<InterviewStartResponse> startOrResume(@AuthenticationPrincipal CustomUserDetails principal) {
        InterviewStartResponse response = interviewService.startOrResume(principal.id());
        CommonResponseCode responseCode = response.resumed() ? CommonResponseCode.SUCCESS : CommonResponseCode.CREATED;
        return ApiResponse.from(responseCode, response);
    }

    @GetMapping("/{sessionId}/progress")
    public ApiResponse<InterviewProgressResponse> getProgress(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long sessionId) {
        InterviewProgressResponse response = interviewProgressQueryService.getProgress(principal.id(), sessionId);

        return ApiResponse.from(CommonResponseCode.SUCCESS, response);
    }

    @GetMapping(value = "/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long sessionId) {
        return interviewProgressSseService.subscribe(principal.id(), sessionId);
    }
}
