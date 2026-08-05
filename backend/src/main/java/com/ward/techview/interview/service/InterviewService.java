package com.ward.techview.interview.service;

import com.ward.techview.account.api.MemberLockManager;
import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.dto.ActiveInterviewResponse;
import com.ward.techview.interview.dto.InterviewStartResponse;
import com.ward.techview.interview.entity.InterviewCoreQuestion;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.entity.InterviewStatus;
import com.ward.techview.interview.entity.InterviewTurn;
import com.ward.techview.interview.repository.InterviewCoreQuestionRepository;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.repository.InterviewTurnRepository;
import com.ward.techview.interview.response.InterviewResponseCode;
import com.ward.techview.question.api.QuestionSelector;
import com.ward.techview.question.api.SelectedQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class InterviewService {
    private static final int CORE_QUESTION_COUNT = 5;

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewCoreQuestionRepository interviewCoreQuestionRepository;
    private final InterviewTurnRepository interviewTurnRepository;
    private final MemberLockManager memberLockManager;
    private final QuestionSelector questionSelector;
    private final InterviewRetentionPolicy retentionPolicy;

    @Transactional(readOnly = true)
    public ActiveInterviewResponse getActiveSession(Long memberId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cutoff = retentionPolicy.calculateCutoff(now);

        return interviewSessionRepository.findActiveSession(memberId, InterviewStatus.IN_PROGRESS, cutoff)
                .map(ActiveInterviewResponse::from)
                .orElseGet(ActiveInterviewResponse::none);
    }

    @Transactional
    public InterviewStartResponse startOrResume(Long memberId) {
        memberLockManager.lock(memberId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cutoff = retentionPolicy.calculateCutoff(now);

        return interviewSessionRepository.findActiveSession(memberId, InterviewStatus.IN_PROGRESS, cutoff)
                .map(this::resume)
                .orElseGet(() -> create(memberId, now));
    }

    private InterviewStartResponse resume(InterviewSession session) {
        InterviewTurn currentTurn = interviewTurnRepository.findCurrentTurn(session.getId(), session.getCurrentTurnOrder())
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.INTERVIEW_STATE_INVALID));

        return InterviewStartResponse.of(true, currentTurn);
    }

    private InterviewStartResponse create(Long memberId, OffsetDateTime startedAt) {
        List<SelectedQuestion> selectedQuestions = questionSelector.selectCoreQuestions();

        validateSelectedQuestions(selectedQuestions);

        InterviewSession session = interviewSessionRepository.save(InterviewSession.create(memberId, startedAt));

        List<InterviewCoreQuestion> coreQuestions = createCoreQuestions(session, selectedQuestions);

        interviewCoreQuestionRepository.saveAll(coreQuestions);

        InterviewTurn firstTurn = interviewTurnRepository.save(
                InterviewTurn.createCore(session, 1, coreQuestions.get(0))
        );

        return InterviewStartResponse.of(false, firstTurn);
    }

    private List<InterviewCoreQuestion> createCoreQuestions(InterviewSession session, List<SelectedQuestion> selectedQuestions) {
        return IntStream.range(0, selectedQuestions.size())
                .mapToObj(index -> {
                    SelectedQuestion selectedQuestion = selectedQuestions.get(index);

                    return InterviewCoreQuestion.create(
                            session,
                            selectedQuestion.id(),
                            selectedQuestion.content(),
                            selectedQuestion.category(),
                            index + 1
                    );
                }).toList();
    }

    private void validateSelectedQuestions(List<SelectedQuestion> selectedQuestions) {
        if (selectedQuestions.size() != CORE_QUESTION_COUNT) {
            throw new BusinessException(InterviewResponseCode.INTERVIEW_STATE_INVALID);
        }
    }
}
