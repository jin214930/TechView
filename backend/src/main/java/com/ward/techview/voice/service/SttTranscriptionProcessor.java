package com.ward.techview.voice.service;

import com.ward.techview.processing.api.ClaimedSttJob;
import com.ward.techview.processing.api.SttJobControl;
import com.ward.techview.voice.api.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttTranscriptionProcessor {
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

    private final SttJobControl sttJobControl;
    private final AudioStorage audioStorage;
    private final SpeechToTextProvider speechToTextProvider;
    private final SttCompletionService completionService;

    public void process(Long processingJobId) {
        sttJobControl.claim(processingJobId)
                .ifPresent(this::processClaimedJob);
    }

    private void processClaimedJob(ClaimedSttJob job) {
        try {
            AudioObject audioObject = audioStorage.download(job.audioObjectKey(), job.sourceObjectEtag());

            TranscriptionResult result = speechToTextProvider.transcribe(audioObject);

            completionService.complete(job, result);
            deleteAudio(job.audioObjectKey());
        } catch (AudioStorageException e) {
            handleStorageFailure(job, e);
        } catch (SpeechToTextException e) {
            handleSttFailure(job, e);
        }
    }

    private void deleteAudio(String objectKey) {
        try {
            audioStorage.delete(objectKey);
        } catch (AudioStorageException e) {
            log.warn("STT 완료 후 음성 객체를 삭제하지 못했습니다. objectKey={}", objectKey, e);
        }
    }

    private void handleStorageFailure(
            ClaimedSttJob job,
            AudioStorageException exception
    ) {
        String errorCode = "AUDIO_STORAGE_" + exception.getFailure().name();

        switch (exception.getFailure()) {
            case TEMPORARY_FAILURE, UNKNOWN -> retry(job, errorCode, exception);
            case OBJECT_NOT_FOUND, VERSION_MISMATCH, ACCESS_DENIED -> fail(job, errorCode, exception);
        }
    }

    private void handleSttFailure(
            ClaimedSttJob job,
            SpeechToTextException exception
    ) {
        String errorCode = "STT_" + exception.getFailure().name();

        switch (exception.getFailure()) {
            case RATE_LIMITED, TEMPORARY_FAILURE, UNKNOWN -> retry(job, errorCode, exception);
            case INVALID_AUDIO, AUTHENTICATION_FAILED -> fail(job, errorCode, exception);
        }
    }

    private void retry(
            ClaimedSttJob job,
            String errorCode,
            RuntimeException exception
    ) {
        OffsetDateTime nextAttemptAt = OffsetDateTime.now(ZoneOffset.UTC).plus(calculateRetryDelay(job.attemptCount()));

        boolean updated = sttJobControl.retry(
                job.processingJobId(),
                job.attemptCount(),
                nextAttemptAt,
                errorCode,
                resolveErrorMessage(exception)
        );

        if (!updated) {
            log.info(
                    "이미 처리 권한이 만료된 STT 작업의 재시도 요청을 무시합니다. " + "processingJobId={}, attemptCount={}",
                    job.processingJobId(),
                    job.attemptCount()
            );
        }
    }

    private void fail(
            ClaimedSttJob job,
            String errorCode,
            RuntimeException exception
    ) {
        boolean updated = sttJobControl.fail(
                job.processingJobId(),
                job.attemptCount(),
                errorCode,
                resolveErrorMessage(exception)
        );

        if (!updated) {
            log.info(
                    "이미 처리 권한이 만료된 STT 작업의 실패 요청을 무시합니다. " + "processingJobId={}, attemptCount={}",
                    job.processingJobId(),
                    job.attemptCount()
            );
        }
    }

    private Duration calculateRetryDelay(int attemptCount) {
        long multiplier = 1L << Math.max(0, attemptCount - 1);

        Duration calculated = INITIAL_RETRY_DELAY.multipliedBy(multiplier);

        if (calculated.compareTo(MAX_RETRY_DELAY) > 0) {
            return MAX_RETRY_DELAY;
        }

        return calculated;
    }

    private String resolveErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message;
    }
}
