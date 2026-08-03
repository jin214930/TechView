package com.ward.techview.voice.service;

import com.ward.techview.processing.api.ClaimedSttJob;
import com.ward.techview.processing.api.SttJobControl;
import com.ward.techview.voice.api.AudioObject;
import com.ward.techview.voice.api.AudioStorage;
import com.ward.techview.voice.api.AudioStorageException;
import com.ward.techview.voice.api.AudioStorageFailure;
import com.ward.techview.voice.api.SpeechToTextException;
import com.ward.techview.voice.api.SpeechToTextFailure;
import com.ward.techview.voice.api.SpeechToTextProvider;
import com.ward.techview.voice.api.TranscriptionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttTranscriptionProcessorTest {

    private static final Long PROCESSING_JOB_ID = 100L;
    private static final String AUDIO_OBJECT_KEY =
            "interviews/10/answers/20/audio.webm";
    private static final String SOURCE_OBJECT_ETAG = "verified-etag";
    private static final ClaimedSttJob JOB = new ClaimedSttJob(
            PROCESSING_JOB_ID,
            10L,
            20L,
            AUDIO_OBJECT_KEY,
            SOURCE_OBJECT_ETAG,
            1
    );
    private static final AudioObject AUDIO_OBJECT =
            new AudioObject(new byte[]{1, 2, 3}, "audio/webm");
    private static final TranscriptionResult RESULT =
            new TranscriptionResult("전사 결과");

    @Mock
    private SttJobControl sttJobControl;

    @Mock
    private AudioStorage audioStorage;

    @Mock
    private SpeechToTextProvider speechToTextProvider;

    @Mock
    private SttCompletionService completionService;

    @InjectMocks
    private SttTranscriptionProcessor processor;

    @Test
    @DisplayName("선점할 STT 작업이 없으면 처리하지 않는다")
    void doesNothingWhenJobCannotBeClaimed() {
        when(sttJobControl.claim(PROCESSING_JOB_ID))
                .thenReturn(Optional.empty());

        processor.process(PROCESSING_JOB_ID);

        verify(sttJobControl).claim(PROCESSING_JOB_ID);
        verifyNoInteractions(
                audioStorage,
                speechToTextProvider,
                completionService
        );
    }

    @Test
    @DisplayName("음성을 전사하고 작업을 완료한 뒤 음성 객체를 삭제한다")
    void processesClaimedJobSuccessfully() {
        prepareSuccessfulProcessing();

        processor.process(PROCESSING_JOB_ID);

        InOrder order = inOrder(
                sttJobControl,
                audioStorage,
                speechToTextProvider,
                completionService
        );
        order.verify(sttJobControl).claim(PROCESSING_JOB_ID);
        order.verify(audioStorage).download(AUDIO_OBJECT_KEY, SOURCE_OBJECT_ETAG);
        order.verify(speechToTextProvider).transcribe(AUDIO_OBJECT);
        order.verify(completionService).complete(JOB, RESULT);
        order.verify(audioStorage).delete(AUDIO_OBJECT_KEY);
    }

    @Test
    @DisplayName("일시적 저장소 오류가 발생하면 작업을 재시도한다")
    void retriesTemporaryStorageFailure() {
        AudioStorageException exception = storageException(
                AudioStorageFailure.TEMPORARY_FAILURE,
                "음성 저장소에 연결할 수 없습니다."
        );
        when(sttJobControl.claim(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(JOB));
        when(audioStorage.download(AUDIO_OBJECT_KEY, SOURCE_OBJECT_ETAG))
                .thenThrow(exception);

        processor.process(PROCESSING_JOB_ID);

        verify(sttJobControl).retry(
                eq(PROCESSING_JOB_ID),
                eq(JOB.attemptCount()),
                any(OffsetDateTime.class),
                eq("AUDIO_STORAGE_TEMPORARY_FAILURE"),
                eq(exception.getMessage())
        );
        verify(sttJobControl, never()).fail(
                any(), any(Integer.class), any(), any()
        );
    }

    @Test
    @DisplayName("음성 객체 ETag가 다르면 작업을 즉시 실패 처리한다")
    void failsVersionMismatchWithoutRetry() {
        AudioStorageException exception = storageException(
                AudioStorageFailure.VERSION_MISMATCH,
                "음성 객체 버전이 다릅니다."
        );
        when(sttJobControl.claim(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(JOB));
        when(audioStorage.download(AUDIO_OBJECT_KEY, SOURCE_OBJECT_ETAG))
                .thenThrow(exception);

        processor.process(PROCESSING_JOB_ID);

        verify(sttJobControl).fail(
                PROCESSING_JOB_ID,
                JOB.attemptCount(),
                "AUDIO_STORAGE_VERSION_MISMATCH",
                exception.getMessage()
        );
        verify(sttJobControl, never()).retry(
                any(), any(Integer.class), any(), any(), any()
        );
    }

    @Test
    @DisplayName("STT 요청 한도를 초과하면 작업을 재시도한다")
    void retriesRateLimitedSttRequest() {
        SpeechToTextException exception = new SpeechToTextException(
                SpeechToTextFailure.RATE_LIMITED,
                "STT 요청 한도를 초과했습니다."
        );
        prepareSttFailure(exception);

        processor.process(PROCESSING_JOB_ID);

        verify(sttJobControl).retry(
                eq(PROCESSING_JOB_ID),
                eq(JOB.attemptCount()),
                any(OffsetDateTime.class),
                eq("STT_RATE_LIMITED"),
                eq(exception.getMessage())
        );
    }

    @Test
    @DisplayName("잘못된 음성이면 STT 작업을 즉시 실패 처리한다")
    void failsInvalidAudioWithoutRetry() {
        SpeechToTextException exception = new SpeechToTextException(
                SpeechToTextFailure.INVALID_AUDIO,
                "지원하지 않는 음성입니다."
        );
        prepareSttFailure(exception);

        processor.process(PROCESSING_JOB_ID);

        verify(sttJobControl).fail(
                PROCESSING_JOB_ID,
                JOB.attemptCount(),
                "STT_INVALID_AUDIO",
                exception.getMessage()
        );
        verify(sttJobControl, never()).retry(
                any(), any(Integer.class), any(), any(), any()
        );
    }

    @Test
    @DisplayName("전사 완료 후 음성 삭제가 실패해도 STT 작업을 다시 처리하지 않는다")
    void ignoresAudioDeletionFailureAfterCompletion() {
        prepareSuccessfulProcessing();
        doThrow(storageException(
                        AudioStorageFailure.TEMPORARY_FAILURE,
                        "음성 객체를 삭제할 수 없습니다."
                ))
                .when(audioStorage)
                .delete(AUDIO_OBJECT_KEY);

        assertDoesNotThrow(() -> processor.process(PROCESSING_JOB_ID));

        verify(completionService).complete(JOB, RESULT);
        verify(sttJobControl, never()).retry(
                any(), any(Integer.class), any(), any(), any()
        );
        verify(sttJobControl, never()).fail(
                any(), any(Integer.class), any(), any()
        );
    }

    private void prepareSuccessfulProcessing() {
        when(sttJobControl.claim(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(JOB));
        when(audioStorage.download(AUDIO_OBJECT_KEY, SOURCE_OBJECT_ETAG))
                .thenReturn(AUDIO_OBJECT);
        when(speechToTextProvider.transcribe(AUDIO_OBJECT))
                .thenReturn(RESULT);
    }

    private void prepareSttFailure(SpeechToTextException exception) {
        when(sttJobControl.claim(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(JOB));
        when(audioStorage.download(AUDIO_OBJECT_KEY, SOURCE_OBJECT_ETAG))
                .thenReturn(AUDIO_OBJECT);
        when(speechToTextProvider.transcribe(AUDIO_OBJECT))
                .thenThrow(exception);
    }

    private AudioStorageException storageException(
            AudioStorageFailure failure,
            String message
    ) {
        return new AudioStorageException(
                failure,
                message,
                new RuntimeException(message)
        );
    }
}
