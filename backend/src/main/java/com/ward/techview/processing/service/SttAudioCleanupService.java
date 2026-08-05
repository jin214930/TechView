package com.ward.techview.processing.service;

import com.ward.techview.processing.api.FailedAudioCleanupTarget;
import com.ward.techview.processing.api.SttAudioCleanupApi;
import com.ward.techview.processing.repository.SttProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SttAudioCleanupService implements SttAudioCleanupApi {
    private final SttProcessingJobRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<FailedAudioCleanupTarget> findDueTargets(OffsetDateTime now, int limit) {
        Objects.requireNonNull(now, "현재 시각은 필수입니다.");

        if (limit <= 0) {
            throw new IllegalArgumentException("조회 개수는 0보다 커야 합니다.");
        }

        return repository.findDueAudioCleanupJobs(now, PageRequest.of(0, limit))
                .stream()
                .map(job -> new FailedAudioCleanupTarget(
                        job.getId(),
                        job.getAudioObjectKey()
                ))
                .toList();
    }

    @Override
    @Transactional
    public boolean markAudioDeleted(Long processingJobId, OffsetDateTime deletedAt) {
        Objects.requireNonNull(processingJobId, "Processing Job ID는 필수입니다.");
        Objects.requireNonNull(deletedAt, "음성 삭제 시각은 필수입니다.");

        return repository.findByIdForUpdate(processingJobId)
                .map(job -> {
                    job.markAudioDeleted(deletedAt);
                    return true;
                })
                .orElse(false);
    }
}
