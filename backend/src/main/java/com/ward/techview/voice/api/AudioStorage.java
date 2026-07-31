package com.ward.techview.voice.api;

public interface AudioStorage {
    PresignedUpload issueUploadUrl(String objectKey, String contentType);
}
