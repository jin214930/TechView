package com.ward.techview.voice.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.api.AnswerCreateCommand;
import com.ward.techview.interview.api.InterviewAnswerApi;
import com.ward.techview.interview.api.PreparedAnswer;
import com.ward.techview.voice.api.AudioStorage;
import com.ward.techview.voice.api.PresignedUpload;
import com.ward.techview.voice.domain.AudioObjectKeyGenerator;
import com.ward.techview.voice.dto.AnswerCreateRequest;
import com.ward.techview.voice.dto.AnswerCreateResponse;
import com.ward.techview.voice.response.VoiceResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AnswerUploadService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("audio/webm");
    private static final int MAX_AUDIO_DURATION_MS = 3 * 60 * 1000;

    private final InterviewAnswerApi interviewAnswerApi;
    private final AudioObjectKeyGenerator audioObjectKeyGenerator;
    private final AudioStorage audioStorage;

    @Transactional
    public AnswerCreateResponse createUpload(Long memberId, Long sessionId, Long turnId, AnswerCreateRequest request) {
        validateContentType(request.audioContentType());
        validateDuration(request.audioDurationMs());

        String objectKey = audioObjectKeyGenerator.generate(sessionId, turnId);

        PreparedAnswer answer = interviewAnswerApi.prepareAnswer(
                new AnswerCreateCommand(
                        memberId,
                        sessionId,
                        turnId,
                        objectKey,
                        request.audioContentType(),
                        request.audioDurationMs()
                )
        );

        PresignedUpload upload = audioStorage.issueUploadUrl(
                answer.audioObjectKey(),
                answer.audioContentType()
        );

        return AnswerCreateResponse.of(answer, upload);
    }

    private void validateContentType(String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(VoiceResponseCode.UNSUPPORTED_AUDIO_CONTENT_TYPE);
        }
    }

    private void validateDuration(int audioDurationMs) {
        if (audioDurationMs <= 0 || audioDurationMs > MAX_AUDIO_DURATION_MS) {
            throw new BusinessException(VoiceResponseCode.AUDIO_DURATION_INVALID);
        }
    }
}
