package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.dto.InterviewProgressResponse;
import com.ward.techview.interview.entity.Answer;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.entity.InterviewStatus;
import com.ward.techview.interview.entity.InterviewTurn;
import com.ward.techview.interview.repository.AnswerRepository;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.repository.InterviewTurnRepository;
import com.ward.techview.interview.response.InterviewResponseCode;
import com.ward.techview.processing.api.AnswerProcessingQuery;
import com.ward.techview.processing.api.AnswerProcessingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewProgressQueryService {
    private static final String PROCESSING_FAILED_MESSAGE = "답변 처리 중 오류가 발생했습니다.";

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewTurnRepository interviewTurnRepository;
    private final AnswerRepository answerRepository;
    private final AnswerProcessingQuery answerProcessingQuery;

    @Transactional(readOnly = true)
    public InterviewProgressResponse getProgress(Long memberId, Long sessionId) {
        InterviewSession session = interviewSessionRepository.findByIdAndMemberId(sessionId, memberId)
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.INTERVIEW_NOT_FOUND));

        if (session.getStatus() == InterviewStatus.COMPLETED) {
            return InterviewProgressResponse.completed(session.getId());
        }

        InterviewTurn currentTurn = interviewTurnRepository.findCurrentTurn(session.getId(), session.getCurrentTurnOrder())
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.INTERVIEW_STATE_INVALID));

        return answerRepository.findByInterviewTurnId(currentTurn.getId())
                .map(answer -> resolveAnsweredTurn(session, currentTurn, answer))
                .orElseGet(() -> InterviewProgressResponse.turnReady(session.getId(), currentTurn));
    }

    private InterviewProgressResponse resolveAnsweredTurn(
            InterviewSession session,
            InterviewTurn currentTurn,
            Answer answer
    ) {
        AnswerProcessingSnapshot snapshot = answerProcessingQuery.getStatus(answer.getId());

        return switch (snapshot.status()) {
            case NOT_STARTED -> InterviewProgressResponse.turnReady(session.getId(), currentTurn);
            case PROCESSING, COMPLETED -> InterviewProgressResponse.processing(session.getId());
            case FAILED ->
                    InterviewProgressResponse.failed(session.getId(), snapshot.errorCode(), PROCESSING_FAILED_MESSAGE);
        };
    }
}
