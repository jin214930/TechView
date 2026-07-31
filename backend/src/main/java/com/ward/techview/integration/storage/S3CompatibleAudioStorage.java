package com.ward.techview.integration.storage;

import com.ward.techview.voice.api.AudioStorage;
import com.ward.techview.voice.api.PresignedUpload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class S3CompatibleAudioStorage implements AudioStorage {
    private final S3Presigner s3Presigner;
    private final AudioStorageProperties properties;

    @Override
    public PresignedUpload issueUploadUrl(String objectKey, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.presignedUrlExpiration())
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return new PresignedUpload(
                URI.create(presignedRequest.url().toString()),
                presignedRequest.expiration().atOffset(ZoneOffset.UTC)
        );
    }
}
