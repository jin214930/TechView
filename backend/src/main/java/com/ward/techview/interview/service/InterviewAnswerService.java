package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.api.AnswerCreateCommand;
import com.ward.techview.interview.api.CreatedAnswer;
import com.ward.techview.interview.api.InterviewAnswerApi;
import com.ward.techview.interview.entity.Answer;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.entity.InterviewStatus;
import com.ward.techview.interview.entity.InterviewTurn;
import com.ward.techview.interview.repository.AnswerRepository;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.repository.InterviewTurnRepository;
import com.ward.techview.interview.response.InterviewResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewAnswerService implements InterviewAnswerApi {
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewTurnRepository interviewTurnRepository;
    private final AnswerRepository answerRepository;

    @Override
    @Transactional
    public CreatedAnswer createAnswer(AnswerCreateCommand command) {
        InterviewSession session = interviewSessionRepository.findByIdAndMemberIdForUpdate(command.sessionId(), command.memberId())
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.INTERVIEW_NOT_FOUND));

        validateInProgress(session);

        InterviewTurn turn = interviewTurnRepository.findByIdAndSessionId(command.turnId(), session.getId())
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.INTERVIEW_TURN_NOT_FOUND));

        validateCurrentTurn(session, turn);
        validateAnswerNotExists(turn);

        Answer answer = Answer.create(turn, command.audioObjectKey(), command.audioContentType(), command.audioDurationMs());
        answerRepository.save(answer);

        return new CreatedAnswer(answer.getId());
    }

    private void validateInProgress(InterviewSession session) {
        if (session.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw new BusinessException(InterviewResponseCode.INTERVIEW_NOT_IN_PROGRESS);
        }
    }

    private void validateCurrentTurn(
            InterviewSession session,
            InterviewTurn turn
    ) {
        if (session.getCurrentTurnOrder() != turn.getTurnOrder()) {
            throw new BusinessException(InterviewResponseCode.INTERVIEW_TURN_NOT_CURRENT);
        }
    }

    private void validateAnswerNotExists(InterviewTurn turn) {
        if (answerRepository.existsByInterviewTurnId(turn.getId())) {
            throw new BusinessException(InterviewResponseCode.ANSWER_ALREADY_EXISTS);
        }
    }
}
