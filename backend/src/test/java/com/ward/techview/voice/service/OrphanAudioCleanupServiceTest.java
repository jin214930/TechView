package com.ward.techview.voice.service;

import com.ward.techview.interview.api.InterviewAudioReferenceApi;
import com.ward.techview.voice.api.AudioStorage;
import com.ward.techview.voice.api.AudioStorageException;
import com.ward.techview.voice.api.AudioStorageFailure;
import com.ward.techview.voice.api.StoredAudioObject;
import com.ward.techview.voice.api.StoredAudioObjectPage;
import com.ward.techview.voice.config.OrphanAudioCleanupProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrphanAudioCleanupServiceTest {
    private static final String PREFIX = "interviews/";
    private static final int PAGE_SIZE = 2;
    private static final OffsetDateTime OLD = OffsetDateTime.of(
            2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC
    );
    private static final OffsetDateTime RECENT = OffsetDateTime.now(ZoneOffset.UTC);

    @Mock
    private AudioStorage audioStorage;

    @Mock
    private InterviewAudioReferenceApi interviewAudioReferenceApi;

    private OrphanAudioCleanupService service;

    @BeforeEach
    void setUp() {
        OrphanAudioCleanupProperties properties = new OrphanAudioCleanupProperties(
                PREFIX,
                Duration.ofHours(24),
                "0 0 5 * * *",
                "Asia/Seoul",
                PAGE_SIZE
        );
        service = new OrphanAudioCleanupService(
                audioStorage,
                interviewAudioReferenceApi,
                properties
        );
    }

    @Test
    @DisplayName("모든 저장소 페이지를 조회하여 참조되지 않은 오래된 음성만 삭제한다")
    void deletesOnlyOldOrphanObjectsAcrossAllPages() {
        StoredAudioObject referenced = object("referenced", OLD);
        StoredAudioObject firstOrphan = object("first-orphan", OLD);
        StoredAudioObject recentOrphan = object("recent-orphan", RECENT);
        StoredAudioObject secondOrphan = object("second-orphan", OLD);

        when(audioStorage.listObjects(PREFIX, null, PAGE_SIZE))
                .thenReturn(new StoredAudioObjectPage(
                        List.of(referenced, firstOrphan, recentOrphan),
                        "next-token"
                ));
        when(audioStorage.listObjects(PREFIX, "next-token", PAGE_SIZE))
                .thenReturn(new StoredAudioObjectPage(List.of(secondOrphan), null));
        when(interviewAudioReferenceApi.findReferencedAudioObjectKeys(
                Set.of(referenced.objectKey(), firstOrphan.objectKey())
        )).thenReturn(Set.of(referenced.objectKey()));
        when(interviewAudioReferenceApi.findReferencedAudioObjectKeys(
                Set.of(secondOrphan.objectKey())
        )).thenReturn(Set.of());

        int deletedCount = service.cleanup();

        assertEquals(2, deletedCount);
        verify(audioStorage, never()).delete(referenced.objectKey(), referenced.etag());
        verify(audioStorage, never()).delete(recentOrphan.objectKey(), recentOrphan.etag());
        verify(audioStorage).delete(firstOrphan.objectKey(), firstOrphan.etag());
        verify(audioStorage).delete(secondOrphan.objectKey(), secondOrphan.etag());

        InOrder order = inOrder(audioStorage);
        order.verify(audioStorage).listObjects(PREFIX, null, PAGE_SIZE);
        order.verify(audioStorage).delete(firstOrphan.objectKey(), firstOrphan.etag());
        order.verify(audioStorage).listObjects(PREFIX, "next-token", PAGE_SIZE);
        order.verify(audioStorage).delete(secondOrphan.objectKey(), secondOrphan.etag());
    }

    @Test
    @DisplayName("한 객체 삭제가 실패해도 나머지 고아 음성을 계속 삭제한다")
    void continuesWhenOneObjectDeletionFails() {
        StoredAudioObject failed = object("failed", OLD);
        StoredAudioObject deleted = object("deleted", OLD);
        when(audioStorage.listObjects(PREFIX, null, PAGE_SIZE))
                .thenReturn(new StoredAudioObjectPage(List.of(failed, deleted), null));
        when(interviewAudioReferenceApi.findReferencedAudioObjectKeys(
                Set.of(failed.objectKey(), deleted.objectKey())
        )).thenReturn(Set.of());
        doThrow(new AudioStorageException(
                AudioStorageFailure.VERSION_MISMATCH,
                "ETag 불일치",
                new RuntimeException()
        )).when(audioStorage).delete(failed.objectKey(), failed.etag());

        int deletedCount = service.cleanup();

        assertEquals(1, deletedCount);
        verify(audioStorage).delete(deleted.objectKey(), deleted.etag());
    }

    @Test
    @DisplayName("보존 기간이 지나지 않은 객체만 있으면 데이터베이스를 조회하지 않는다")
    void skipsReferenceLookupWhenNoObjectIsOldEnough() {
        when(audioStorage.listObjects(PREFIX, null, PAGE_SIZE))
                .thenReturn(new StoredAudioObjectPage(
                        List.of(object("recent", RECENT)),
                        null
                ));

        int deletedCount = service.cleanup();

        assertEquals(0, deletedCount);
        verify(interviewAudioReferenceApi, never())
                .findReferencedAudioObjectKeys(anySet());
    }

    private static StoredAudioObject object(String key, OffsetDateTime lastModifiedAt) {
        return new StoredAudioObject(PREFIX + key, "etag-" + key, lastModifiedAt);
    }
}
