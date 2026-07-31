package com.ward.techview.voice.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.api.AnswerCreateCommand;
import com.ward.techview.interview.api.CreatedAnswer;
import com.ward.techview.interview.api.InterviewAnswerApi;
import com.ward.techview.voice.api.AudioStorage;
import com.ward.techview.voice.api.PresignedUpload;
import com.ward.techview.voice.domain.AudioObjectKeyGenerator;
import com.ward.techview.voice.dto.AnswerCreateRequest;
import com.ward.techview.voice.dto.AnswerCreateResponse;
import com.ward.techview.voice.response.VoiceResponseCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
    private static final String CONTENT_TYPE = "audio/webm";
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

    @InjectMocks
    private AnswerUploadService answerUploadService;

    @Test
    @DisplayName("답변을 생성하고 음성 업로드 URL을 발급한다")
    void shouldCreateAnswerAndIssueUploadUrl() {
        AnswerCreateRequest request = new AnswerCreateRequest(CONTENT_TYPE, DURATION_MS);
        when(audioObjectKeyGenerator.generate(SESSION_ID, TURN_ID)).thenReturn(OBJECT_KEY);
        when(interviewAnswerApi.createAnswer(any(AnswerCreateCommand.class)))
                .thenReturn(new CreatedAnswer(ANSWER_ID));
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
                () -> assertEquals(UPLOAD_URL, response.uploadUrl()),
                () -> assertEquals(EXPIRES_AT, response.uploadExpiresAt())
        );

        ArgumentCaptor<AnswerCreateCommand> commandCaptor =
                ArgumentCaptor.forClass(AnswerCreateCommand.class);
        verify(interviewAnswerApi).createAnswer(commandCaptor.capture());
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
        order.verify(interviewAnswerApi).createAnswer(any(AnswerCreateCommand.class));
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
        verify(interviewAnswerApi, never()).createAnswer(any());
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
        verify(interviewAnswerApi, never()).createAnswer(any());
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
        verify(interviewAnswerApi, never()).createAnswer(any());
        verify(audioStorage, never()).issueUploadUrl(any(), any());
    }

    @Test
    @DisplayName("업로드 URL 발급 실패 예외를 호출자에게 전달한다")
    void shouldPropagateStorageFailure() {
        AnswerCreateRequest request = new AnswerCreateRequest(CONTENT_TYPE, DURATION_MS);
        RuntimeException storageFailure = new RuntimeException("storage failure");
        when(audioObjectKeyGenerator.generate(SESSION_ID, TURN_ID)).thenReturn(OBJECT_KEY);
        when(interviewAnswerApi.createAnswer(any(AnswerCreateCommand.class)))
                .thenReturn(new CreatedAnswer(ANSWER_ID));
        when(audioStorage.issueUploadUrl(OBJECT_KEY, CONTENT_TYPE)).thenThrow(storageFailure);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> answerUploadService.createUpload(MEMBER_ID, SESSION_ID, TURN_ID, request)
        );

        assertSame(storageFailure, exception);
        verify(interviewAnswerApi).createAnswer(any(AnswerCreateCommand.class));
    }
}
