package com.ward.techview.voice.service;

import com.ward.techview.interview.api.AnswerTranscriptionCommand;
import com.ward.techview.interview.api.InterviewAnswerApi;
import com.ward.techview.processing.api.ClaimedSttJob;
import com.ward.techview.processing.api.SttJobControl;
import com.ward.techview.voice.api.TranscriptionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class SttCompletionService {
    private final InterviewAnswerApi interviewAnswerApi;
    private final SttJobControl sttJobControl;

    @Transactional
    public void completeTranscription(ClaimedSttJob job, TranscriptionResult result) {
        interviewAnswerApi.completeTranscription(
                new AnswerTranscriptionCommand(
                        job.interviewSessionId(),
                        job.answerId(),
                        result.transcript(),
                        OffsetDateTime.now(ZoneOffset.UTC)
                )
        );

        boolean advanced = sttJobControl.advanceToCleanup(
                job.processingJobId(),
                job.attemptCount()
        );

        if (!advanced) {
            throw new IllegalStateException("STT 작업 처리 권한이 만료되었습니다.");
        }
    }
}
