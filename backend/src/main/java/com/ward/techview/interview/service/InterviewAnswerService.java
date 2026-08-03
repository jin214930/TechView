package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.api.AnswerCreateCommand;
import com.ward.techview.interview.api.AnswerInfo;
import com.ward.techview.interview.api.InterviewAnswerApi;
import com.ward.techview.interview.api.PreparedAnswer;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewAnswerService implements InterviewAnswerApi {
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewTurnRepository interviewTurnRepository;
    private final AnswerRepository answerRepository;

    @Override
    @Transactional
    public PreparedAnswer prepareAnswer(AnswerCreateCommand command) {
        InterviewSession session = interviewSessionRepository.findByIdAndMemberIdForUpdate(command.sessionId(), command.memberId())
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.INTERVIEW_NOT_FOUND));

        validateInProgress(session);

        InterviewTurn turn = interviewTurnRepository.findByIdAndSessionId(command.turnId(), session.getId())
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.INTERVIEW_TURN_NOT_FOUND));

        validateCurrentTurn(session, turn);

        return answerRepository.findByInterviewTurnId(turn.getId())
                .map(answer -> prepareExistingAnswer(answer, command))
                .orElseGet(() -> createAnswer(turn, command));
    }

    @Override
    @Transactional(readOnly = true)
    public AnswerInfo getAnswer(Long memberId, Long answerId) {
        Answer answer = answerRepository.findByIdAndMemberId(answerId, memberId)
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.ANSWER_NOT_FOUND));

        return toAnswerInfo(answer);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AnswerInfo lockAnswerForProcessing(Long memberId, Long interviewSessionId, Long answerId) {
        InterviewSession session = interviewSessionRepository.findByIdAndMemberIdForUpdate(interviewSessionId, memberId)
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.INTERVIEW_NOT_FOUND));

        validateInProgress(session);

        Answer answer = answerRepository.findByIdAndSessionId(answerId, session.getId())
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.ANSWER_NOT_FOUND));

        validateCurrentTurn(session, answer.getInterviewTurn());

        return toAnswerInfo(answer);
    }

    private PreparedAnswer createAnswer(InterviewTurn turn, AnswerCreateCommand command) {
        Answer answer = Answer.create(turn, command.audioObjectKey(), command.audioContentType(), command.audioDurationMs());
        answerRepository.save(answer);

        return toPreparedAnswer(answer, true);
    }

    private PreparedAnswer prepareExistingAnswer(Answer answer, AnswerCreateCommand command) {
        if (!answer.getAudioContentType().equals(command.audioContentType())
                || answer.getAudioDurationMs() != command.audioDurationMs()) {
            throw new BusinessException(InterviewResponseCode.ANSWER_UPLOAD_METADATA_MISMATCH);
        }

        return toPreparedAnswer(answer, false);
    }

    private PreparedAnswer toPreparedAnswer(Answer answer, boolean created) {
        return new PreparedAnswer(
                answer.getId(),
                answer.getAudioObjectKey(),
                answer.getAudioContentType(),
                created
        );
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

    private AnswerInfo toAnswerInfo(Answer answer) {
        return new AnswerInfo(
                answer.getId(),
                answer.getInterviewTurn().getSession().getId(),
                answer.getAudioObjectKey(),
                answer.getAudioContentType()
        );
    }
}
