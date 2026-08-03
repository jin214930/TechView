package com.ward.techview.voice.service;

import com.ward.techview.interview.api.AnswerInfo;
import com.ward.techview.interview.api.InterviewAnswerApi;
import com.ward.techview.processing.api.ProcessingJobQueue;
import com.ward.techview.processing.api.ProcessingJobReceipt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttJobRegistrationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long ANSWER_ID = 1000L;
    private static final Long PROCESSING_JOB_ID = 2000L;

    @Mock
    private InterviewAnswerApi interviewAnswerApi;

    @Mock
    private ProcessingJobQueue processingJobQueue;

    @InjectMocks
    private SttJobRegistrationService sttJobRegistrationService;

    @Test
    @DisplayName("면접 세션과 답변을 잠근 후 STT 작업을 생성한다")
    void shouldLockAnswerBeforeCreatingSttJob() {
        AnswerInfo answer = new AnswerInfo(
                ANSWER_ID,
                SESSION_ID,
                "interviews/10/turns/100/audio-key",
                "audio/webm"
        );
        ProcessingJobReceipt expected = new ProcessingJobReceipt(
                PROCESSING_JOB_ID,
                SESSION_ID,
                ANSWER_ID
        );
        when(interviewAnswerApi.lockAnswerForProcessing(
                MEMBER_ID,
                SESSION_ID,
                ANSWER_ID
        )).thenReturn(answer);
        when(processingJobQueue.createOrGetSttJob(SESSION_ID, ANSWER_ID))
                .thenReturn(expected);

        ProcessingJobReceipt result =
                sttJobRegistrationService.register(MEMBER_ID, answer);

        assertSame(expected, result);
        InOrder order = inOrder(interviewAnswerApi, processingJobQueue);
        order.verify(interviewAnswerApi).lockAnswerForProcessing(
                MEMBER_ID,
                SESSION_ID,
                ANSWER_ID
        );
        order.verify(processingJobQueue).createOrGetSttJob(
                SESSION_ID,
                ANSWER_ID
        );
    }
}
