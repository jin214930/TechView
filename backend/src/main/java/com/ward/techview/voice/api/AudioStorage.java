package com.ward.techview.voice.api;

import java.util.Optional;

public interface AudioStorage {
    PresignedUpload issueUploadUrl(String objectKey, String contentType);

    Optional<AudioObjectMetadata> findMetadata(String objectKey);

    AudioObject download(String objectKey, String expectedEtag);

    void delete(String objectKey, String expectedEtag);

    StoredAudioObjectPage listObjects(String prefix, String continuationToken, int limit);
}
