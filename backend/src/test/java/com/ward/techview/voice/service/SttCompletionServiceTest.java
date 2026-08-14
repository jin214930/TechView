package com.ward.techview.voice.service;

import com.ward.techview.interview.api.answer.AnswerTranscriptionCommand;
import com.ward.techview.interview.api.answer.InterviewAnswerApi;
import com.ward.techview.processing.api.stt.ClaimedSttJob;
import com.ward.techview.processing.api.job.ProcessingJobQueue;
import com.ward.techview.processing.api.stt.SttJobControl;
import com.ward.techview.voice.api.stt.TranscriptionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttCompletionServiceTest {

    private static final ClaimedSttJob JOB = new ClaimedSttJob(
            100L,
            10L,
            20L,
            "interviews/10/answers/20/audio.webm",
            "verified-etag",
            false,
            1
    );
    private static final TranscriptionResult RESULT =
            new TranscriptionResult("의존성 주입은 객체의 의존 관계를 외부에서 제공하는 방식입니다.");

    @Mock
    private InterviewAnswerApi interviewAnswerApi;

    @Mock
    private SttJobControl sttJobControl;

    @Mock
    private ProcessingJobQueue processingJobQueue;

    @InjectMocks
    private SttCompletionService sttCompletionService;

    @Test
    @DisplayName("전사 결과 저장과 정리 단계 전환 후 답변 분석 작업을 생성한다")
    void completesAnswerBeforeAdvancingJobToCleanup() {
        when(sttJobControl.advanceToCleanup(JOB.processingJobId(), JOB.attemptCount()))
                .thenReturn(true);

        sttCompletionService.completeTranscription(JOB, RESULT);

        ArgumentCaptor<AnswerTranscriptionCommand> commandCaptor =
                ArgumentCaptor.forClass(AnswerTranscriptionCommand.class);
        InOrder order = inOrder(interviewAnswerApi, sttJobControl, processingJobQueue);
        order.verify(interviewAnswerApi).completeTranscription(commandCaptor.capture());
        order.verify(sttJobControl).advanceToCleanup(
                JOB.processingJobId(),
                JOB.attemptCount()
        );
        order.verify(processingJobQueue).createOrGetAnswerAnalysisJob(
                JOB.interviewSessionId(),
                JOB.answerId()
        );
        AnswerTranscriptionCommand command = commandCaptor.getValue();
        assertAll(
                () -> assertEquals(JOB.interviewSessionId(), command.interviewSessionId()),
                () -> assertEquals(JOB.answerId(), command.answerId()),
                () -> assertEquals(RESULT.transcript(), command.transcript())
        );
    }

    @Test
    @DisplayName("작업 처리 권한이 만료되면 예외를 발생시킨다")
    void throwsWhenJobAttemptIsNoLongerCurrent() {
        when(sttJobControl.advanceToCleanup(JOB.processingJobId(), JOB.attemptCount()))
                .thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> sttCompletionService.completeTranscription(JOB, RESULT)
        );

        verify(interviewAnswerApi).completeTranscription(
                org.mockito.ArgumentMatchers.any(AnswerTranscriptionCommand.class)
        );
        verifyNoInteractions(processingJobQueue);
    }
}
