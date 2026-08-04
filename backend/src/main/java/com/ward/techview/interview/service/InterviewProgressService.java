package com.ward.techview.interview.service;

import com.ward.techview.interview.api.InterviewAdvanceCommand;
import com.ward.techview.interview.api.InterviewProgressApi;
import com.ward.techview.interview.entity.*;
import com.ward.techview.interview.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewProgressService implements InterviewProgressApi {
    private final InterviewSessionRepository interviewSessionRepository;
    private final AnswerRepository answerRepository;
    private final InterviewCoreQuestionRepository coreQuestionRepository;
    private final FollowUpQuestionRepository followUpQuestionRepository;
    private final InterviewTurnRepository interviewTurnRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void advanceAfterAnalysis(InterviewAdvanceCommand command) {
        InterviewSession session = interviewSessionRepository
                .findByIdForUpdate(command.interviewSessionId())
                .orElseThrow(() -> new IllegalArgumentException("면접 세션을 찾을 수 없습니다."));

        Answer answer = answerRepository.findByIdAndSessionIdForUpdate(command.answerId(), session.getId())
                .orElseThrow(() -> new IllegalArgumentException("분석 대상 답변을 찾을 수 없습니다."));

        validateAnswer(session, answer);

        InterviewTurn currentTurn = answer.getInterviewTurn();
        InterviewCoreQuestion currentCoreQuestion = resolveCoreQuestion(currentTurn);

        if (command.requiresFollowUp()) {
            createFollowUpTurn(session, currentTurn, currentCoreQuestion, command.followUpQuestion());
            return;
        }

        createNextCoreTurnOrComplete(session, currentCoreQuestion, command);
    }

    private void validateAnswer(InterviewSession session, Answer answer) {
        InterviewTurn turn = answer.getInterviewTurn();

        if (session.getCurrentTurnOrder() != turn.getTurnOrder()) {
            throw new IllegalStateException("현재 면접 턴의 답변만 처리할 수 있습니다.");
        }

        if (!answer.isTranscribed()) {
            throw new IllegalStateException("전사가 완료된 답변만 분석 완료 처리할 수 있습니다.");
        }
    }

    private InterviewCoreQuestion resolveCoreQuestion(InterviewTurn turn) {
        return switch (turn.getTurnType()) {
            case CORE -> turn.getInterviewCoreQuestion();
            case FOLLOW_UP -> turn.getFollowUpQuestion().getCoreQuestion();
        };
    }

    private void createFollowUpTurn(InterviewSession session, InterviewTurn currentTurn, InterviewCoreQuestion coreQuestion, String question) {
        if (currentTurn.getTurnType() != InterviewTurnType.CORE) {
            throw new IllegalStateException("꼬리 질문 답변에는 추가 꼬리 질문을 생성할 수 없습니다.");
        }

        FollowUpQuestion followUpQuestion = FollowUpQuestion.create(coreQuestion, question);

        followUpQuestionRepository.save(followUpQuestion);

        int nextTurnOrder = session.advanceTurn();

        InterviewTurn followUpTurn = InterviewTurn.createFollowUp(session, nextTurnOrder, followUpQuestion);

        interviewTurnRepository.save(followUpTurn);
    }

    private void createNextCoreTurnOrComplete(InterviewSession session, InterviewCoreQuestion currentCoreQuestion, InterviewAdvanceCommand command) {
        int nextQuestionOrder = currentCoreQuestion.getQuestionOrder() + 1;

        coreQuestionRepository.findBySessionIdAndQuestionOrder(session.getId(), nextQuestionOrder)
                .ifPresentOrElse(
                        nextCoreQuestion -> createCoreTurn(session, nextCoreQuestion),
                        () -> session.complete(command.analyzedAt())
                );
    }

    private void createCoreTurn(InterviewSession session, InterviewCoreQuestion coreQuestion) {
        int nextTurnOrder = session.advanceTurn();

        InterviewTurn nextTurn = InterviewTurn.createCore(
                session,
                nextTurnOrder,
                coreQuestion
        );

        interviewTurnRepository.save(nextTurn);
    }
}
