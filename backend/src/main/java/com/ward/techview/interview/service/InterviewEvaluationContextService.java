package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.api.AnswerAnalysisContext;
import com.ward.techview.interview.api.InterviewEvaluationContextApi;
import com.ward.techview.interview.api.InterviewFeedbackContext;
import com.ward.techview.interview.api.InterviewFeedbackTurnContext;
import com.ward.techview.interview.entity.*;
import com.ward.techview.interview.repository.AnswerRepository;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.response.InterviewResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewEvaluationContextService implements InterviewEvaluationContextApi {
    private final AnswerRepository answerRepository;
    private final InterviewSessionRepository interviewSessionRepository;

    @Override
    @Transactional(readOnly = true)
    public AnswerAnalysisContext getAnalysisContext(Long interviewSessionId, Long answerId) {
        Answer answer = answerRepository
                .findAnalysisTarget(interviewSessionId, answerId)
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.ANSWER_NOT_FOUND));

        if (!answer.isTranscribed()) {
            throw new IllegalStateException("전사가 완료된 답변만 분석할 수 있습니다.");
        }

        return toAnalysisContext(answer);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewFeedbackContext getFinalFeedbackContext(Long interviewSessionId) {
        InterviewSession session = interviewSessionRepository
                .findById(interviewSessionId)
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.INTERVIEW_NOT_FOUND));

        if (session.getStatus() != InterviewStatus.COMPLETED) {
            throw new IllegalStateException("완료된 면접만 종합 피드백을 생성할 수 있습니다.");
        }

        List<InterviewFeedbackTurnContext> turns = answerRepository
                .findAllFeedbackTargets(interviewSessionId)
                .stream()
                .map(this::toFeedbackTurnContext)
                .toList();

        if (turns.isEmpty()) {
            throw new IllegalStateException("종합 피드백을 생성할 답변이 없습니다.");
        }

        if (turns.size() != session.getCurrentTurnOrder()) {
            throw new IllegalStateException("모든 면접 답변이 준비되어야 종합 피드백을 생성할 수 있습니다.");
        }

        return new InterviewFeedbackContext(interviewSessionId, turns);
    }

    private AnswerAnalysisContext toAnalysisContext(Answer answer) {
        InterviewTurn turn = answer.getInterviewTurn();

        return switch (turn.getTurnType()) {
            case CORE -> toCoreAnalysisContext(answer, turn);
            case FOLLOW_UP -> toFollowUpAnalysisContext(answer, turn);
        };
    }

    private AnswerAnalysisContext toCoreAnalysisContext(Answer answer, InterviewTurn turn) {
        InterviewCoreQuestion coreQuestion = turn.getInterviewCoreQuestion();

        return new AnswerAnalysisContext(
                turn.getSession().getId(),
                answer.getId(),
                coreQuestion.getCategory(),
                coreQuestion.getContent(),
                null,
                answer.getTranscript()
        );
    }

    private AnswerAnalysisContext toFollowUpAnalysisContext(Answer answer, InterviewTurn turn) {
        FollowUpQuestion followUpQuestion = turn.getFollowUpQuestion();
        InterviewCoreQuestion coreQuestion = followUpQuestion.getCoreQuestion();

        return new AnswerAnalysisContext(
                turn.getSession().getId(),
                answer.getId(),
                coreQuestion.getCategory(),
                coreQuestion.getContent(),
                followUpQuestion.getContent(),
                answer.getTranscript()
        );
    }

    private InterviewFeedbackTurnContext toFeedbackTurnContext(Answer answer) {
        if (!answer.isTranscribed()) {
            throw new IllegalStateException("전사가 완료된 답변만 종합 피드백에 포함할 수 있습니다.");
        }

        InterviewTurn turn = answer.getInterviewTurn();

        return switch (turn.getTurnType()) {
            case CORE -> toCoreFeedbackTurnContext(answer, turn);
            case FOLLOW_UP -> toFollowUpFeedbackTurnContext(answer, turn);
        };
    }

    private InterviewFeedbackTurnContext toCoreFeedbackTurnContext(Answer answer, InterviewTurn turn) {
        InterviewCoreQuestion coreQuestion = turn.getInterviewCoreQuestion();

        return new InterviewFeedbackTurnContext(
                answer.getId(),
                turn.getTurnOrder(),
                coreQuestion.getCategory(),
                coreQuestion.getContent(),
                null,
                answer.getTranscript()
        );
    }

    private InterviewFeedbackTurnContext toFollowUpFeedbackTurnContext(Answer answer, InterviewTurn turn) {
        FollowUpQuestion followUpQuestion = turn.getFollowUpQuestion();
        InterviewCoreQuestion coreQuestion = followUpQuestion.getCoreQuestion();

        return new InterviewFeedbackTurnContext(
                answer.getId(),
                turn.getTurnOrder(),
                coreQuestion.getCategory(),
                coreQuestion.getContent(),
                followUpQuestion.getContent(),
                answer.getTranscript()
        );
    }
}
