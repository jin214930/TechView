package com.ward.techview.integration.storage;

import com.ward.techview.voice.api.storage.*;
import com.ward.techview.voice.api.stt.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
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

            throw mapStorageException(objectKey, e);
        } catch (SdkClientException e) {
            throw new AudioStorageException(AudioStorageFailure.TEMPORARY_FAILURE, "음성 저장소에 연결할 수 없습니다.", e);
        }
    }

    @Override
    public AudioObject download(String objectKey, String expectedEtag) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .ifMatch(expectedEtag)
                .build();

        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            return new AudioObject(response.asByteArray(), response.response().contentType());
        } catch (S3Exception e) {
            throw mapStorageException(objectKey, e);
        } catch (SdkClientException e) {
            throw new AudioStorageException(AudioStorageFailure.TEMPORARY_FAILURE, "음성 저장소에 연결할 수 없습니다.", e);
        }
    }

    @Override
    public void delete(String objectKey, String expectedEtag) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .ifMatch(expectedEtag)
                .build();

        deleteObject(objectKey, request);
    }

    @Override
    public StoredAudioObjectPage listObjects(String prefix, String continuationToken, int limit) {
        validateListObjectsRequest(prefix, continuationToken, limit);

        ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                .bucket(properties.bucket())
                .prefix(prefix)
                .maxKeys(limit);

        if (continuationToken != null) {
            requestBuilder.continuationToken(continuationToken);
        }

        try {
            ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());

            List<StoredAudioObject> objects = response.contents()
                    .stream()
                    .map(object -> new StoredAudioObject(
                            object.key(),
                            object.eTag(),
                            object.lastModified().atOffset(ZoneOffset.UTC)
                    ))
                    .toList();

            String nextContinuationToken = Boolean.TRUE.equals(response.isTruncated())
                    ? response.nextContinuationToken()
                    : null;

            return new StoredAudioObjectPage(objects, nextContinuationToken);
        } catch (S3Exception exception) {
            throw mapStorageException(prefix, exception);
        } catch (SdkClientException exception) {
            throw new AudioStorageException(
                    AudioStorageFailure.TEMPORARY_FAILURE,
                    "음성 저장소의 객체 목록을 조회할 수 없습니다.",
                    exception
            );
        }
    }

    private AudioStorageException mapStorageException(String objectKey, S3Exception exception) {
        AudioStorageFailure failure = switch (exception.statusCode()) {
            case 404 -> AudioStorageFailure.OBJECT_NOT_FOUND;
            case 412 -> AudioStorageFailure.VERSION_MISMATCH;
            case 401, 403 -> AudioStorageFailure.ACCESS_DENIED;
            case 429, 500, 502, 503, 504 -> AudioStorageFailure.TEMPORARY_FAILURE;
            default -> AudioStorageFailure.UNKNOWN;
        };

        return new AudioStorageException(failure, "음성 객체를 처리할 수 없습니다: " + objectKey, exception);
    }

    private void deleteObject(String objectKey, DeleteObjectRequest request) {
        try {
            s3Client.deleteObject(request);
        } catch (S3Exception exception) {
            if (exception.statusCode() == NOT_FOUND_STATUS_CODE) {
                return;
            }

            throw mapStorageException(objectKey, exception);
        } catch (SdkClientException exception) {
            throw new AudioStorageException(AudioStorageFailure.TEMPORARY_FAILURE, "음성 객체를 삭제할 수 없습니다.", exception);
        }
    }

    private void validateListObjectsRequest(String prefix, String continuationToken, int limit) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("조회할 객체 접두사는 필수입니다.");
        }

        if (continuationToken != null && continuationToken.isBlank()) {
            throw new IllegalArgumentException("페이지 토큰은 비어 있을 수 없습니다.");
        }

        if (limit <= 0 || limit > 1_000) {
            throw new IllegalArgumentException("객체 조회 개수는 1 이상 1000 이하여야 합니다.");
        }
    }
}
