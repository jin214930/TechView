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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerUploadServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long TURN_ID = 100L;
    private static final Long ANSWER_ID = 1000L;
    private static final Long PROCESSING_JOB_ID = 2000L;
    private static final String CONTENT_TYPE = "audio/webm";
    private static final String ETAG = "verified-audio-etag";
    private static final int DURATION_MS = 42_000;
    private static final String OBJECT_KEY = "interviews/10/turns/100/audio-key";
    private static final URI UPLOAD_URL = URI.create("http://localhost:9000/techview-audio/upload");
    private static final OffsetDateTime EXPIRES_AT =
            OffsetDateTime.of(2026, 7, 31, 12, 5, 0, 0, ZoneOffset.UTC);

    @Mock
    private InterviewAnswerApi interviewAnswerApi;

    @Mock
    private AudioObjectKeyGenerator audioObjectKeyGenerator;

    @Mock
    private AudioStorage audioStorage;

    @Mock
    private VoiceUploadProperties voiceUploadProperties;

    @Mock
    private SttJobRegistrationService sttJobRegistrationService;

    @Mock
    private ProcessingJobQueue processingJobQueue;

    @InjectMocks
    private AnswerUploadService answerUploadService;

    @Test
    @DisplayName("답변을 생성하고 음성 업로드 URL을 발급한다")
    void shouldCreateAnswerAndIssueUploadUrl() {
        AnswerCreateRequest request = new AnswerCreateRequest(CONTENT_TYPE, DURATION_MS);
        when(audioObjectKeyGenerator.generate(SESSION_ID, TURN_ID)).thenReturn(OBJECT_KEY);
        when(interviewAnswerApi.prepareAnswer(any(AnswerCreateCommand.class)))
                .thenReturn(new PreparedAnswer(ANSWER_ID, OBJECT_KEY, CONTENT_TYPE, true));
        when(audioStorage.issueUploadUrl(OBJECT_KEY, CONTENT_TYPE))
                .thenReturn(new PresignedUpload(UPLOAD_URL, EXPIRES_AT));

        AnswerCreateResponse response = answerUploadService.createUpload(
                MEMBER_ID,
                SESSION_ID,
                TURN_ID,
                request
        );

        assertAll(
                () -> assertEquals(ANSWER_ID, response.answerId()),
                () -> assertEquals(true, response.created()),
                () -> assertEquals(UPLOAD_URL, response.uploadUrl()),
                () -> assertEquals(EXPIRES_AT, response.uploadExpiresAt())
        );

        ArgumentCaptor<AnswerCreateCommand> commandCaptor =
                ArgumentCaptor.forClass(AnswerCreateCommand.class);
        verify(interviewAnswerApi).prepareAnswer(commandCaptor.capture());
        AnswerCreateCommand command = commandCaptor.getValue();
        assertAll(
                () -> assertEquals(MEMBER_ID, command.memberId()),
                () -> assertEquals(SESSION_ID, command.sessionId()),
                () -> assertEquals(TURN_ID, command.turnId()),
                () -> assertEquals(OBJECT_KEY, command.audioObjectKey()),
                () -> assertEquals(CONTENT_TYPE, command.audioContentType()),
                () -> assertEquals(DURATION_MS, command.audioDurationMs())
        );

        InOrder order = inOrder(audioObjectKeyGenerator, interviewAnswerApi, audioStorage);
        order.verify(audioObjectKeyGenerator).generate(SESSION_ID, TURN_ID);
        order.verify(interviewAnswerApi).prepareAnswer(any(AnswerCreateCommand.class));
        order.verify(audioStorage).issueUploadUrl(OBJECT_KEY, CONTENT_TYPE);
    }

    @Test
    @DisplayName("지원하지 않는 음성 형식이면 답변과 업로드 URL을 생성하지 않는다")
    void shouldRejectUnsupportedContentType() {
        AnswerCreateRequest request = new AnswerCreateRequest("audio/wav", DURATION_MS);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> answerUploadService.createUpload(MEMBER_ID, SESSION_ID, TURN_ID, request)
        );

        assertSame(VoiceResponseCode.UNSUPPORTED_AUDIO_CONTENT_TYPE, exception.getResponseCode());
        verify(audioObjectKeyGenerator, never()).generate(any(), any());
        verify(interviewAnswerApi, never()).prepareAnswer(any());
        verify(audioStorage, never()).issueUploadUrl(any(), any());
    }

    @Test
    @DisplayName("음성 재생 시간이 0이면 답변과 업로드 URL을 생성하지 않는다")
    void shouldRejectZeroDuration() {
        AnswerCreateRequest request = new AnswerCreateRequest(CONTENT_TYPE, 0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> answerUploadService.createUpload(MEMBER_ID, SESSION_ID, TURN_ID, request)
        );

        assertSame(VoiceResponseCode.AUDIO_DURATION_INVALID, exception.getResponseCode());
        verify(interviewAnswerApi, never()).prepareAnswer(any());
        verify(audioStorage, never()).issueUploadUrl(any(), any());
    }

    @Test
    @DisplayName("음성 재생 시간이 3분을 초과하면 답변과 업로드 URL을 생성하지 않는다")
    void shouldRejectDurationOverMaximum() {
        AnswerCreateRequest request = new AnswerCreateRequest(CONTENT_TYPE, 180_001);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> answerUploadService.createUpload(MEMBER_ID, SESSION_ID, TURN_ID, request)
        );

        assertSame(VoiceResponseCode.AUDIO_DURATION_INVALID, exception.getResponseCode());
        verify(interviewAnswerApi, never()).prepareAnswer(any());
        verify(audioStorage, never()).issueUploadUrl(any(), any());
    }

    @Test
    @DisplayName("업로드 URL 발급 실패 예외를 호출자에게 전달한다")
    void shouldPropagateStorageFailure() {
        AnswerCreateRequest request = new AnswerCreateRequest(CONTENT_TYPE, DURATION_MS);
        RuntimeException storageFailure = new RuntimeException("storage failure");
        when(audioObjectKeyGenerator.generate(SESSION_ID, TURN_ID)).thenReturn(OBJECT_KEY);
        when(interviewAnswerApi.prepareAnswer(any(AnswerCreateCommand.class)))
                .thenReturn(new PreparedAnswer(ANSWER_ID, OBJECT_KEY, CONTENT_TYPE, true));
        when(audioStorage.issueUploadUrl(OBJECT_KEY, CONTENT_TYPE)).thenThrow(storageFailure);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> answerUploadService.createUpload(MEMBER_ID, SESSION_ID, TURN_ID, request)
        );

        assertSame(storageFailure, exception);
        verify(interviewAnswerApi).prepareAnswer(any(AnswerCreateCommand.class));
    }

    @Test
    @DisplayName("기존 답변에는 저장된 객체 키로 업로드 URL을 재발급한다")
    void shouldReissueUploadUrlForExistingAnswer() {
        String unusedGeneratedKey = "interviews/10/turns/100/unused-new-key";
        AnswerCreateRequest request = new AnswerCreateRequest(CONTENT_TYPE, DURATION_MS);
        when(audioObjectKeyGenerator.generate(SESSION_ID, TURN_ID)).thenReturn(unusedGeneratedKey);
        when(interviewAnswerApi.prepareAnswer(any(AnswerCreateCommand.class)))
                .thenReturn(new PreparedAnswer(ANSWER_ID, OBJECT_KEY, CONTENT_TYPE, false));
        when(audioStorage.issueUploadUrl(OBJECT_KEY, CONTENT_TYPE))
                .thenReturn(new PresignedUpload(UPLOAD_URL, EXPIRES_AT));

        AnswerCreateResponse response = answerUploadService.createUpload(
                MEMBER_ID,
                SESSION_ID,
                TURN_ID,
                request
        );

        assertAll(
                () -> assertEquals(ANSWER_ID, response.answerId()),
                () -> assertEquals(false, response.created()),
                () -> assertEquals(UPLOAD_URL, response.uploadUrl())
        );
        verify(audioStorage).issueUploadUrl(OBJECT_KEY, CONTENT_TYPE);
        verify(audioStorage, never()).issueUploadUrl(unusedGeneratedKey, CONTENT_TYPE);
    }

    @Test
    @DisplayName("이미 제출된 답변이면 업로드 URL을 재발급하지 않는다")
    void shouldRejectUploadUrlReissueWhenAnswerAlreadySubmitted() {
        AnswerCreateRequest request = new AnswerCreateRequest(CONTENT_TYPE, DURATION_MS);
        when(audioObjectKeyGenerator.generate(SESSION_ID, TURN_ID)).thenReturn(OBJECT_KEY);
        when(interviewAnswerApi.prepareAnswer(any(AnswerCreateCommand.class)))
                .thenReturn(new PreparedAnswer(ANSWER_ID, OBJECT_KEY, CONTENT_TYPE, false));
        when(processingJobQueue.hasSttJob(ANSWER_ID)).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> answerUploadService.createUpload(
                        MEMBER_ID,
                        SESSION_ID,
                        TURN_ID,
                        request
                )
        );

        assertSame(VoiceResponseCode.ANSWER_ALREADY_SUBMITTED, exception.getResponseCode());
        verify(audioStorage, never()).issueUploadUrl(any(), any());
    }

    @Test
    @DisplayName("기존 STT 작업이 있으면 저장소를 조회하지 않고 완료 결과를 반환한다")
    void shouldReturnExistingSttJobWithoutStorageLookup() {
        AnswerInfo answer = answerInfo();
        ProcessingJobReceipt job = processingJobReceipt();
        when(interviewAnswerApi.getAnswer(MEMBER_ID, ANSWER_ID)).thenReturn(answer);
        when(processingJobQueue.findSttJob(ANSWER_ID)).thenReturn(Optional.of(job));

        AnswerUploadCompletionResponse response =
                answerUploadService.completeUpload(MEMBER_ID, ANSWER_ID);

        assertAll(
                () -> assertEquals(ANSWER_ID, response.answerId()),
                () -> assertEquals(PROCESSING_JOB_ID, response.processingJobId())
        );
        verify(audioStorage, never()).findMetadata(any());
        verify(sttJobRegistrationService, never()).register(any(), any(), any());
    }

    @Test
    @DisplayName("업로드된 음성 객체가 없으면 완료 처리를 거부한다")
    void shouldRejectCompletionWhenAudioObjectDoesNotExist() {
        AnswerInfo answer = answerInfo();
        when(interviewAnswerApi.getAnswer(MEMBER_ID, ANSWER_ID)).thenReturn(answer);
        when(processingJobQueue.findSttJob(ANSWER_ID)).thenReturn(Optional.empty());
        when(audioStorage.findMetadata(OBJECT_KEY)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> answerUploadService.completeUpload(MEMBER_ID, ANSWER_ID)
        );

        assertSame(VoiceResponseCode.AUDIO_OBJECT_NOT_FOUND, exception.getResponseCode());
        verify(sttJobRegistrationService, never()).register(any(), any(), any());
    }

    @Test
    @DisplayName("업로드된 음성 형식이 답변 정보와 다르면 완료 처리를 거부한다")
    void shouldRejectCompletionWhenAudioContentTypeDoesNotMatch() {
        AnswerInfo answer = answerInfo();
        when(interviewAnswerApi.getAnswer(MEMBER_ID, ANSWER_ID)).thenReturn(answer);
        when(processingJobQueue.findSttJob(ANSWER_ID)).thenReturn(Optional.empty());
        when(audioStorage.findMetadata(OBJECT_KEY))
                .thenReturn(Optional.of(new AudioObjectMetadata("audio/wav", 1_024L, ETAG)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> answerUploadService.completeUpload(MEMBER_ID, ANSWER_ID)
        );

        assertSame(VoiceResponseCode.AUDIO_CONTENT_TYPE_MISMATCH, exception.getResponseCode());
        verify(sttJobRegistrationService, never()).register(any(), any(), any());
    }

    @Test
    @DisplayName("업로드된 음성 파일이 비어 있으면 완료 처리를 거부한다")
    void shouldRejectCompletionWhenAudioFileIsEmpty() {
        AnswerInfo answer = answerInfo();
        when(interviewAnswerApi.getAnswer(MEMBER_ID, ANSWER_ID)).thenReturn(answer);
        when(processingJobQueue.findSttJob(ANSWER_ID)).thenReturn(Optional.empty());
        when(audioStorage.findMetadata(OBJECT_KEY))
                .thenReturn(Optional.of(new AudioObjectMetadata(CONTENT_TYPE, 0L, ETAG)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> answerUploadService.completeUpload(MEMBER_ID, ANSWER_ID)
        );

        assertSame(VoiceResponseCode.AUDIO_FILE_EMPTY, exception.getResponseCode());
        verify(sttJobRegistrationService, never()).register(any(), any(), any());
    }

    @Test
    @DisplayName("업로드된 음성 파일이 허용 크기를 초과하면 완료 처리를 거부한다")
    void shouldRejectCompletionWhenAudioFileExceedsMaximumSize() {
        AnswerInfo answer = answerInfo();
        when(interviewAnswerApi.getAnswer(MEMBER_ID, ANSWER_ID)).thenReturn(answer);
        when(processingJobQueue.findSttJob(ANSWER_ID)).thenReturn(Optional.empty());
        when(audioStorage.findMetadata(OBJECT_KEY))
                .thenReturn(Optional.of(new AudioObjectMetadata(CONTENT_TYPE, 1_025L, ETAG)));
        when(voiceUploadProperties.maxFileSize()).thenReturn(DataSize.ofBytes(1_024L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> answerUploadService.completeUpload(MEMBER_ID, ANSWER_ID)
        );

        assertSame(VoiceResponseCode.AUDIO_FILE_SIZE_EXCEEDED, exception.getResponseCode());
        verify(sttJobRegistrationService, never()).register(any(), any(), any());
    }

    @Test
    @DisplayName("업로드된 음성 객체가 유효하면 STT 작업을 등록한다")
    void shouldRegisterSttJobWhenUploadedAudioObjectIsValid() {
        AnswerInfo answer = answerInfo();
        ProcessingJobReceipt job = processingJobReceipt();
        when(interviewAnswerApi.getAnswer(MEMBER_ID, ANSWER_ID)).thenReturn(answer);
        when(processingJobQueue.findSttJob(ANSWER_ID)).thenReturn(Optional.empty());
        when(audioStorage.findMetadata(OBJECT_KEY))
                .thenReturn(Optional.of(new AudioObjectMetadata(CONTENT_TYPE, 1_024L, ETAG)));
        when(voiceUploadProperties.maxFileSize()).thenReturn(DataSize.ofMegabytes(25));
        when(sttJobRegistrationService.register(MEMBER_ID, answer, ETAG)).thenReturn(job);

        AnswerUploadCompletionResponse response =
                answerUploadService.completeUpload(MEMBER_ID, ANSWER_ID);

        assertAll(
                () -> assertEquals(ANSWER_ID, response.answerId()),
                () -> assertEquals(PROCESSING_JOB_ID, response.processingJobId())
        );
        verify(sttJobRegistrationService).register(MEMBER_ID, answer, ETAG);
    }

    private static AnswerInfo answerInfo() {
        return new AnswerInfo(
                ANSWER_ID,
                SESSION_ID,
                OBJECT_KEY,
                CONTENT_TYPE
        );
    }

    private static ProcessingJobReceipt processingJobReceipt() {
        return new ProcessingJobReceipt(
                PROCESSING_JOB_ID,
                SESSION_ID
        );
    }
}
