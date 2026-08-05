package com.ward.techview.interview.service;

import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.processing.api.SessionProcessingCleanupApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ExpiredInterviewCleanupBatchService {
    private final InterviewSessionRepository interviewSessionRepository;
    private final SessionProcessingCleanupApi sessionProcessingCleanupApi;

    @Transactional
    public ExpiredInterviewCleanupBatchResult cleanupBatch(OffsetDateTime now, OffsetDateTime cutoff, long afterSessionId, int limit) {
        validateArguments(now, cutoff, afterSessionId, limit);

        List<InterviewSession> sessions = interviewSessionRepository
                .findExpiredSessionsForUpdateSkipLocked(
                        cutoff,
                        afterSessionId,
                        limit
                );

        if (sessions.isEmpty()) {
            return ExpiredInterviewCleanupBatchResult.empty(afterSessionId);
        }

        int deletedCount = 0;

        for (InterviewSession session : sessions) {
            if (!session.isExpired(cutoff)) {
                continue;
            }

            boolean canDelete = sessionProcessingCleanupApi.canDeleteSession(session.getId(), now);

            if (!canDelete) {
                continue;
            }

            interviewSessionRepository.delete(session);
            deletedCount++;
        }

        long nextCursor = sessions.getLast().getId();

        return new ExpiredInterviewCleanupBatchResult(
                nextCursor,
                sessions.size(),
                deletedCount
        );
    }

    private void validateArguments(OffsetDateTime now, OffsetDateTime cutoff, long afterSessionId, int limit) {
        Objects.requireNonNull(now, "현재 시각은 필수입니다.");
        Objects.requireNonNull(cutoff, "면접 만료 기준 시각은 필수입니다.");

        if (afterSessionId < 0) {
            throw new IllegalArgumentException("마지막 면접 세션 ID는 0 이상이어야 합니다.");
        }

        if (limit <= 0) {
            throw new IllegalArgumentException("정리 배치 크기는 0보다 커야 합니다.");
        }
    }
}
