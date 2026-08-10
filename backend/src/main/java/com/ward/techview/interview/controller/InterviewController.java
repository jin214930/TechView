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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/interviews")
@SecurityRequirement(name = "bearerAuth")
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
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "일일 면접 횟수 제한 초과")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "진행 중인 면접 재개"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새 면접 시작")
    })
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
    @Operation(summary = "면접 진행 이벤트 구독", description = "fetch 기반 SSE로 턴 준비·처리 실패·면접 및 피드백 완료 이벤트를 수신합니다.")
    public SseEmitter subscribe(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long sessionId) {
        return interviewProgressSseService.subscribe(principal.id(), sessionId);
    }
}
