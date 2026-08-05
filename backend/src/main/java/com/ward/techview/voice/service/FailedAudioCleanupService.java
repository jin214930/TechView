package com.ward.techview.voice.service;

import com.ward.techview.processing.api.FailedAudioCleanupTarget;
import com.ward.techview.processing.api.SttAudioCleanupApi;
import com.ward.techview.voice.api.AudioStorage;
import com.ward.techview.voice.api.AudioStorageException;
import com.ward.techview.voice.config.FailedAudioCleanupProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailedAudioCleanupService {
    private final SttAudioCleanupApi sttAudioCleanupApi;
    private final AudioStorage audioStorage;
    private final FailedAudioCleanupProperties properties;

    public int cleanup() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        long cursor = 0L;
        int totalDeletedCount = 0;

        while (true) {
            List<FailedAudioCleanupTarget> targets = sttAudioCleanupApi.findDueTargets(
                    now,
                    cursor,
                    properties.batchSize()
            );

            if (targets.isEmpty()) {
                break;
            }

            for (FailedAudioCleanupTarget target : targets) {
                if (delete(target)) {
                    totalDeletedCount++;
                }
            }

            cursor = targets.getLast().processingJobId();

            if (targets.size() < properties.batchSize()) {
                break;
            }
        }

        return totalDeletedCount;
    }

    private boolean delete(FailedAudioCleanupTarget target) {
        try {
            audioStorage.delete(
                    target.audioObjectKey(),
                    target.sourceObjectEtag()
            );
        } catch (AudioStorageException exception) {
            log.warn(
                    "실패 음성 객체 정리에 실패했습니다. processingJobId={}, objectKey={}, failure={}",
                    target.processingJobId(),
                    target.audioObjectKey(),
                    exception.getFailure(),
                    exception
            );

            return false;
        }

        OffsetDateTime deletedAt = OffsetDateTime.now(ZoneOffset.UTC);

        boolean marked = sttAudioCleanupApi.markAudioDeleted(target.processingJobId(), deletedAt);

        if (!marked) {
            log.info(
                    "삭제 완료 처리할 STT 작업이 존재하지 않습니다. processingJobId={}, objectKey={}",
                    target.processingJobId(),
                    target.audioObjectKey()
            );
        }

        return marked;
    }
}
