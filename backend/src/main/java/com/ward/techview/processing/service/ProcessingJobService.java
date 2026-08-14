package com.ward.techview.processing.service;

import com.ward.techview.processing.api.analysis.*;
import com.ward.techview.processing.api.cleanup.*;
import com.ward.techview.processing.api.feedback.*;
import com.ward.techview.processing.api.job.*;
import com.ward.techview.processing.api.stt.*;
import com.ward.techview.processing.entity.AnswerAnalysisProcessingJob;
import com.ward.techview.processing.entity.FinalFeedbackProcessingJob;
import com.ward.techview.processing.entity.ProcessingJob;
import com.ward.techview.processing.entity.SttProcessingJob;
import com.ward.techview.processing.repository.AnswerAnalysisProcessingJobRepository;
import com.ward.techview.processing.repository.FinalFeedbackProcessingJobRepository;
import com.ward.techview.processing.repository.SttProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProcessingJobService implements ProcessingJobQueue {
    private final SttProcessingJobRepository sttProcessingJobRepository;
    private final AnswerAnalysisProcessingJobRepository answerAnalysisProcessingJobRepository;
    private final FinalFeedbackProcessingJobRepository finalFeedbackProcessingJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessingJobReceipt> findSttJob(Long answerId) {
        return sttProcessingJobRepository.findByAnswerId(answerId)
                .map(this::toReceipt);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSttJob(Long answerId) {
        return sttProcessingJobRepository.existsByAnswerId(answerId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProcessingJobReceipt createOrGetSttJob(
            Long interviewSessionId,
            Long answerId,
            String audioObjectKey,
            String sourceObjectEtag
    ) {
        return sttProcessingJobRepository.findByAnswerId(answerId)
                .map(this::toReceipt)
                .orElseGet(() -> createSttJob(
                        interviewSessionId,
                        answerId,
                        audioObjectKey,
                        sourceObjectEtag
                ));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProcessingJobReceipt createOrGetAnswerAnalysisJob(Long interviewSessionId, Long answerId) {
        return answerAnalysisProcessingJobRepository.findByAnswerId(answerId)
                .map(this::toReceipt)
                .orElseGet(() -> createAnswerAnalysisJob(interviewSessionId, answerId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProcessingJobReceipt createOrGetFinalFeedbackJob(Long interviewSessionId) {
        return finalFeedbackProcessingJobRepository
                .findByInterviewSessionId(interviewSessionId)
                .map(this::toReceipt)
                .orElseGet(() -> createFinalFeedbackJob(interviewSessionId));
    }

    private ProcessingJobReceipt toReceipt(ProcessingJob job) {
        return new ProcessingJobReceipt(
                job.getId(),
                job.getInterviewSessionId()
        );
    }

    private ProcessingJobReceipt createSttJob(
            Long interviewSessionId,
            Long answerId,
            String audioObjectKey,
            String sourceObjectEtag
    ) {
        SttProcessingJob job = SttProcessingJob.create(
                interviewSessionId,
                answerId,
                audioObjectKey,
                sourceObjectEtag,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        sttProcessingJobRepository.save(job);

        eventPublisher.publishEvent(new SttTranscriptionJobQueuedEvent(job.getId()));

        return toReceipt(job);
    }

    private ProcessingJobReceipt createAnswerAnalysisJob(Long interviewSessionId, Long answerId) {
        AnswerAnalysisProcessingJob job = AnswerAnalysisProcessingJob.create(
                interviewSessionId,
                answerId,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        answerAnalysisProcessingJobRepository.save(job);

        eventPublisher.publishEvent(new AnswerAnalysisJobQueuedEvent(job.getId()));

        return toReceipt(job);
    }

    private ProcessingJobReceipt createFinalFeedbackJob(Long interviewSessionId) {
        FinalFeedbackProcessingJob job = FinalFeedbackProcessingJob.create(interviewSessionId, OffsetDateTime.now(ZoneOffset.UTC));

        finalFeedbackProcessingJobRepository.save(job);

        eventPublisher.publishEvent(new FinalFeedbackJobQueuedEvent(job.getId()));

        return toReceipt(job);
    }
}
