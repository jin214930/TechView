package com.ward.techview.voice.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.api.answer.AnswerCreateCommand;
import com.ward.techview.interview.api.answer.AnswerInfo;
import com.ward.techview.interview.api.answer.InterviewAnswerApi;
import com.ward.techview.interview.api.answer.PreparedAnswer;
import com.ward.techview.processing.api.job.ProcessingJobQueue;
import com.ward.techview.processing.api.job.ProcessingJobReceipt;
import com.ward.techview.voice.api.storage.AudioObjectMetadata;
import com.ward.techview.voice.api.storage.AudioStorage;
import com.ward.techview.voice.api.storage.PresignedUpload;
import com.ward.techview.voice.config.VoiceUploadProperties;
import com.ward.techview.voice.domain.AudioObjectKeyGenerator;
import com.ward.techview.voice.dto.AnswerCreateRequest;
import com.ward.techview.voice.dto.AnswerCreateResponse;
import com.ward.techview.voice.dto.AnswerUploadCompletionResponse;
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
    private final VoiceUploadProperties voiceUploadProperties;
    private final SttJobRegistrationService sttJobRegistrationService;
    private final ProcessingJobQueue processingJobQueue;

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

        if (processingJobQueue.hasSttJob(answer.id())) {
            throw new BusinessException(VoiceResponseCode.ANSWER_ALREADY_SUBMITTED);
        }

        PresignedUpload upload = audioStorage.issueUploadUrl(
                answer.audioObjectKey(),
                answer.audioContentType()
        );

        return AnswerCreateResponse.of(answer, upload);
    }


    public AnswerUploadCompletionResponse completeUpload(Long memberId, Long answerId) {
        AnswerInfo answer = interviewAnswerApi.getAnswer(memberId, answerId);

        return processingJobQueue.findSttJob(answerId)
                .map(job -> AnswerUploadCompletionResponse.from(answerId, job))
                .orElseGet(() -> {
                    AudioObjectMetadata metadata = audioStorage
                            .findMetadata(answer.audioObjectKey())
                            .orElseThrow(() ->
                                    new BusinessException(
                                            VoiceResponseCode.AUDIO_OBJECT_NOT_FOUND
                                    )
                            );

                    validateUploadedObject(answer, metadata);

                    ProcessingJobReceipt job = sttJobRegistrationService.register(
                            memberId,
                            answer,
                            metadata.etag()
                    );

                    return AnswerUploadCompletionResponse.from(answer.answerId(), job);
                });
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

    private void validateUploadedObject(AnswerInfo answer, AudioObjectMetadata metadata) {
        if (!answer.audioContentType().equals(metadata.contentType())) {
            throw new BusinessException(
                    VoiceResponseCode.AUDIO_CONTENT_TYPE_MISMATCH
            );
        }

        if (metadata.contentLength() <= 0) {
            throw new BusinessException(
                    VoiceResponseCode.AUDIO_FILE_EMPTY
            );
        }

        if (metadata.contentLength()
                > voiceUploadProperties.maxFileSize().toBytes()) {
            throw new BusinessException(
                    VoiceResponseCode.AUDIO_FILE_SIZE_EXCEEDED
            );
        }
    }
}
