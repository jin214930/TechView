package com.ward.techview.integration.storage;

import com.ward.techview.voice.api.AudioObjectMetadata;
import com.ward.techview.voice.api.AudioStorage;
import com.ward.techview.voice.api.PresignedUpload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class S3CompatibleAudioStorage implements AudioStorage {
    private static final int NOT_FOUND_STATUS_CODE = 404;

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
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

    @Override
    public Optional<AudioObjectMetadata> findMetadata(String objectKey) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();

        try {
            HeadObjectResponse response = s3Client.headObject(request);

            return Optional.of(new AudioObjectMetadata(
                    response.contentType(),
                    response.contentLength(),
                    response.eTag()
            ));
        } catch (S3Exception e) {
            if (e.statusCode() == NOT_FOUND_STATUS_CODE) {
                return Optional.empty();
            }

            throw e;
        }
    }
}
