package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.api.feedback.InterviewFeedbackAccessApi;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.entity.InterviewStatus;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.response.InterviewResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InterviewFeedbackAccessService implements InterviewFeedbackAccessApi {
    private final InterviewSessionRepository interviewSessionRepository;

    @Override
    @Transactional(readOnly = true)
    public void validateCompletedInterview(Long memberId, Long interviewSessionId) {
        Objects.requireNonNull(memberId, "회원 ID는 필수입니다.");
        Objects.requireNonNull(interviewSessionId, "면접 세션 ID는 필수입니다.");

        InterviewSession session = interviewSessionRepository.findByIdAndMemberId(interviewSessionId, memberId)
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.INTERVIEW_NOT_FOUND));

        if (session.getStatus() != InterviewStatus.COMPLETED) {
            throw new BusinessException(InterviewResponseCode.INTERVIEW_NOT_COMPLETED);
        }
    }
}
