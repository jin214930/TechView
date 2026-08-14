package com.ward.techview.voice.service;

import com.ward.techview.interview.api.answer.AnswerInfo;
import com.ward.techview.interview.api.answer.InterviewAnswerApi;
import com.ward.techview.processing.api.job.ProcessingJobQueue;
import com.ward.techview.processing.api.job.ProcessingJobReceipt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SttJobRegistrationService {
    private final InterviewAnswerApi interviewAnswerApi;
    private final ProcessingJobQueue processingJobQueue;

    @Transactional
    public ProcessingJobReceipt register(
            Long memberId,
            AnswerInfo answer,
            String sourceObjectEtag
    ) {
        AnswerInfo lockedAnswer = interviewAnswerApi.lockAnswerForProcessing(
                memberId,
                answer.interviewSessionId(),
                answer.answerId()
        );

        return processingJobQueue.createOrGetSttJob(
                lockedAnswer.interviewSessionId(),
                lockedAnswer.answerId(),
                lockedAnswer.audioObjectKey(),
                sourceObjectEtag
        );
    }
}
