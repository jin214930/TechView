package com.ward.techview.voice.service;

import com.ward.techview.interview.api.InterviewAudioReferenceApi;
import com.ward.techview.voice.api.AudioStorage;
import com.ward.techview.voice.api.AudioStorageException;
import com.ward.techview.voice.api.StoredAudioObject;
import com.ward.techview.voice.api.StoredAudioObjectPage;
import com.ward.techview.voice.config.OrphanAudioCleanupProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanAudioCleanupService {
    private final AudioStorage audioStorage;
    private final InterviewAudioReferenceApi interviewAudioReferenceApi;
    private final OrphanAudioCleanupProperties properties;

    public int cleanup() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(properties.gracePeriod());

        String continuationToken = null;
        int totalDeletedCount = 0;

        do {
            StoredAudioObjectPage page = audioStorage.listObjects(
                    properties.prefix(),
                    continuationToken,
                    properties.pageSize()
            );

            totalDeletedCount += cleanupPage(page.objects(), cutoff);
            continuationToken = page.nextContinuationToken();
        } while (continuationToken != null);

        return totalDeletedCount;
    }

    private int cleanupPage(List<StoredAudioObject> objects, OffsetDateTime cutoff) {
        List<StoredAudioObject> candidates = objects.stream()
                .filter(object -> object.lastModifiedAt().isBefore(cutoff))
                .toList();

        if (candidates.isEmpty()) {
            return 0;
        }

        Set<String> candidateKeys = candidates.stream()
                .map(StoredAudioObject::objectKey)
                .collect(Collectors.toSet());

        Set<String> referencedKeys =
                interviewAudioReferenceApi.findReferencedAudioObjectKeys(candidateKeys);

        int deletedCount = 0;

        for (StoredAudioObject candidate : candidates) {
            if (referencedKeys.contains(candidate.objectKey())) {
                continue;
            }

            if (delete(candidate)) {
                deletedCount++;
            }
        }

        return deletedCount;
    }

    private boolean delete(StoredAudioObject object) {
        try {
            audioStorage.delete(object.objectKey(), object.etag());
            return true;
        } catch (AudioStorageException exception) {
            log.warn(
                    "고아 음성 객체 삭제에 실패했습니다. objectKey={}, failure={}",
                    object.objectKey(),
                    exception.getFailure(),
                    exception
            );

            return false;
        }
    }
}
