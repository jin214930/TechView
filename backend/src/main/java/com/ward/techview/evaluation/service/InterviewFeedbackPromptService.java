package com.ward.techview.evaluation.service;

import com.ward.techview.evaluation.api.feedback.InterviewFeedbackPrompt;
import com.ward.techview.evaluation.api.feedback.InterviewFeedbackTurnPrompt;
import com.ward.techview.evaluation.entity.AnswerAnalysis;
import com.ward.techview.evaluation.repository.AnswerAnalysisRepository;
import com.ward.techview.interview.api.feedback.InterviewEvaluationContextApi;
import com.ward.techview.interview.api.feedback.InterviewFeedbackContext;
import com.ward.techview.interview.api.feedback.InterviewFeedbackTurnContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
public class InterviewFeedbackPromptService {
    private final InterviewEvaluationContextApi interviewEvaluationContextApi;
    private final AnswerAnalysisRepository answerAnalysisRepository;

    @Transactional(readOnly = true)
    public InterviewFeedbackPrompt create(Long interviewSessionId) {
        InterviewFeedbackContext context = interviewEvaluationContextApi.getFinalFeedbackContext(interviewSessionId);

        List<Long> answerIds = context.turns()
                .stream()
                .map(InterviewFeedbackTurnContext::answerId)
                .toList();

        Map<Long, AnswerAnalysis> analysisByAnswerId = answerAnalysisRepository.findAllByAnswerIdIn(answerIds)
                .stream()
                .collect(toMap(AnswerAnalysis::getAnswerId, identity()));

        if (analysisByAnswerId.size() != answerIds.size()) {
            throw new IllegalStateException("모든 답변의 분석이 완료되어야 종합 피드백을 생성할 수 있습니다.");
        }

        List<InterviewFeedbackTurnPrompt> turns = context.turns()
                .stream()
                .map(turn -> toTurnPrompt(turn, analysisByAnswerId))
                .toList();

        return new InterviewFeedbackPrompt(context.interviewSessionId(), turns);
    }

    private InterviewFeedbackTurnPrompt toTurnPrompt(InterviewFeedbackTurnContext turn, Map<Long, AnswerAnalysis> analysisByAnswerId) {
        AnswerAnalysis analysis = analysisByAnswerId.get(turn.answerId());

        if (analysis == null) {
            throw new IllegalStateException("답변 분석 결과가 존재하지 않습니다. answerId=" + turn.answerId());
        }

        return new InterviewFeedbackTurnPrompt(
                turn.answerId(),
                turn.turnOrder(),
                turn.category(),
                turn.coreQuestion(),
                turn.followUpQuestion(),
                turn.transcript(),
                analysis.getSummary(),
                analysis.getStrengths(),
                analysis.getImprovements()
        );
    }
}
