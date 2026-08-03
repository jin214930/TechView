package com.ward.techview.integration.storage;

import com.ward.techview.voice.api.AudioObjectMetadata;
import com.ward.techview.voice.api.PresignedUpload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3CompatibleAudioStorageTest {

    private static final URI ENDPOINT = URI.create("http://localhost:9000");
    private static final Duration EXPIRATION = Duration.ofMinutes(5);

    private S3Presigner s3Presigner;
    private S3Client s3Client;
    private S3CompatibleAudioStorage audioStorage;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3Presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .endpointOverride(ENDPOINT)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("test-access-key", "test-secret-key")
                        )
                )
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .build();

        AudioStorageProperties properties = new AudioStorageProperties(
                "techview-audio",
                "ap-northeast-2",
                ENDPOINT,
                "test-access-key",
                "test-secret-key",
                EXPIRATION,
                true
        );
        audioStorage = new S3CompatibleAudioStorage(s3Presigner, s3Client, properties);
    }

    @AfterEach
    void tearDown() {
        s3Presigner.close();
    }

    @Test
    @DisplayName("Content-Type이 서명된 S3 호환 PUT URL을 발급한다")
    void shouldIssuePresignedPutUrlWithSignedContentType() {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(4);

        PresignedUpload upload = audioStorage.issueUploadUrl(
                "interviews/10/turns/100/audio-key",
                "audio/webm"
        );

        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(6);
        URI uploadUrl = upload.uploadUrl();
        assertAll(
                () -> assertEquals("http", uploadUrl.getScheme()),
                () -> assertEquals("localhost", uploadUrl.getHost()),
                () -> assertEquals(9000, uploadUrl.getPort()),
                () -> assertEquals(
                        "/techview-audio/interviews/10/turns/100/audio-key",
                        uploadUrl.getPath()
                ),
                () -> assertTrue(uploadUrl.getRawQuery().contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")),
                () -> assertTrue(uploadUrl.getRawQuery().contains("X-Amz-Signature=")),
                () -> assertTrue(uploadUrl.getRawQuery().contains("content-type")),
                () -> assertTrue(upload.expiresAt().isAfter(before)),
                () -> assertTrue(upload.expiresAt().isBefore(after))
        );
    }

    @Test
    @DisplayName("저장된 음성 객체의 메타데이터를 조회한다")
    void shouldFindAudioObjectMetadata() {
        HeadObjectResponse headObjectResponse = HeadObjectResponse.builder()
                .contentType("audio/webm")
                .contentLength(1_024L)
                .build();
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(headObjectResponse);

        Optional<AudioObjectMetadata> result = audioStorage.findMetadata(
                "interviews/10/turns/100/audio-key"
        );

        assertTrue(result.isPresent());
        assertAll(
                () -> assertEquals("audio/webm", result.get().contentType()),
                () -> assertEquals(1_024L, result.get().contentLength())
        );
    }

    @Test
    @DisplayName("저장소에 음성 객체가 없으면 빈 결과를 반환한다")
    void shouldReturnEmptyWhenAudioObjectDoesNotExist() {
        S3Exception notFound = mock(S3Exception.class);
        when(notFound.statusCode()).thenReturn(404);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(notFound);

        Optional<AudioObjectMetadata> result = audioStorage.findMetadata(
                "interviews/10/turns/100/missing-audio-key"
        );

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("저장소 조회 오류는 호출자에게 전달한다")
    void shouldPropagateStorageFailureWhenFindingMetadata() {
        S3Exception storageFailure = mock(S3Exception.class);
        when(storageFailure.statusCode()).thenReturn(503);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(storageFailure);

        S3Exception exception = assertThrows(
                S3Exception.class,
                () -> audioStorage.findMetadata(
                        "interviews/10/turns/100/audio-key"
                )
        );

        assertSame(storageFailure, exception);
    }

    @Test
    @DisplayName("Presigned URL 만료 시간이 0이면 설정 생성을 거절한다")
    void shouldRejectZeroPresignedUrlExpiration() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new AudioStorageProperties(
                        "techview-audio",
                        "ap-northeast-2",
                        ENDPOINT,
                        "test-access-key",
                        "test-secret-key",
                        Duration.ZERO,
                        true
                )
        );
    }
}
