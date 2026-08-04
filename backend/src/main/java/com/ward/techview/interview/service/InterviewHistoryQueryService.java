package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.response.CommonResponseCode;
import com.ward.techview.interview.api.CompletedInterviewSummary;
import com.ward.techview.interview.api.InterviewHistoryQueryApi;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.entity.InterviewStatus;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InterviewHistoryQueryService implements InterviewHistoryQueryApi {
    private static final int MAX_PAGE_SIZE = 100;

    private final InterviewSessionRepository interviewSessionRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<CompletedInterviewSummary> getCompletedInterviews(Long memberId, int page, int size) {
        Objects.requireNonNull(memberId, "회원 ID는 필수입니다.");
        validatePageRequest(page, size);

        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id")
        );

        return interviewSessionRepository.findAllByMemberIdAndStatus(memberId, InterviewStatus.COMPLETED, pageRequest)
                .map(this::toSummary);
    }

    private CompletedInterviewSummary toSummary(InterviewSession session) {
        return new CompletedInterviewSummary(
                session.getId(),
                session.getStartedAt(),
                session.getCompletedAt()
        );
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonResponseCode.INVALID_REQUEST);
        }
    }
}
