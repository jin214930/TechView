package com.ward.techview.voice.controller;

import com.ward.techview.common.response.ApiResponse;
import com.ward.techview.common.response.CommonResponseCode;
import com.ward.techview.common.security.CustomUserDetails;
import com.ward.techview.voice.dto.AnswerCreateRequest;
import com.ward.techview.voice.dto.AnswerCreateResponse;
import com.ward.techview.voice.dto.AnswerUploadCompletionResponse;
import com.ward.techview.voice.response.VoiceResponseCode;
import com.ward.techview.voice.service.AnswerUploadService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class AnswerUploadController {
    private final AnswerUploadService answerUploadService;

    @PostMapping("/interviews/{sessionId}/turns/{turnId}/answers")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "기존 답변의 업로드 URL 재발급"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "답변 생성 및 업로드 URL 발급")
    })
    public ApiResponse<AnswerCreateResponse> uploadAnswer(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long sessionId,
            @PathVariable Long turnId,
            @Valid @RequestBody AnswerCreateRequest request
    ) {
        AnswerCreateResponse response = answerUploadService.createUpload(principal.id(), sessionId, turnId, request);
        CommonResponseCode responseCode = response.created()
                ? CommonResponseCode.CREATED
                : CommonResponseCode.SUCCESS;
        return ApiResponse.from(responseCode, response);
    }

    @PostMapping("/answers/{answerId}/upload-completion")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "음성 처리 작업 접수")
    public ApiResponse<AnswerUploadCompletionResponse> completeAnswerUpload(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long answerId
    ) {
        AnswerUploadCompletionResponse response = answerUploadService.completeUpload(principal.id(), answerId);
        return ApiResponse.from(VoiceResponseCode.ANSWER_UPLOAD_ACCEPTED, response);
    }
}
