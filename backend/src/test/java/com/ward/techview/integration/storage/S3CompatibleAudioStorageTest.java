package com.ward.techview.integration.storage;

import com.ward.techview.voice.api.AudioObject;
import com.ward.techview.voice.api.AudioObjectMetadata;
import com.ward.techview.voice.api.AudioStorageException;
import com.ward.techview.voice.api.AudioStorageFailure;
import com.ward.techview.voice.api.PresignedUpload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
                .eTag("verified-audio-etag")
                .build();
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(headObjectResponse);

        Optional<AudioObjectMetadata> result = audioStorage.findMetadata(
                "interviews/10/turns/100/audio-key"
        );

        assertTrue(result.isPresent());
        assertAll(
                () -> assertEquals("audio/webm", result.get().contentType()),
                () -> assertEquals(1_024L, result.get().contentLength()),
                () -> assertEquals("verified-audio-etag", result.get().etag())
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

        AudioStorageException exception = assertThrows(
                AudioStorageException.class,
                () -> audioStorage.findMetadata(
                        "interviews/10/turns/100/audio-key"
                )
        );

        assertAll(
                () -> assertSame(AudioStorageFailure.TEMPORARY_FAILURE, exception.getFailure()),
                () -> assertSame(storageFailure, exception.getCause())
        );
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

    @Test
    @DisplayName("검증된 ETag를 If-Match 조건으로 사용해 음성 객체를 다운로드한다")
    void shouldDownloadAudioObjectWithExpectedEtag() {
        byte[] content = {1, 2, 3, 4};
        GetObjectResponse getObjectResponse = GetObjectResponse.builder()
                .contentType("audio/webm")
                .build();
        ResponseBytes<GetObjectResponse> response = ResponseBytes.fromByteArray(
                getObjectResponse,
                content
        );
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(response);

        AudioObject result = audioStorage.download(
                "interviews/10/turns/100/audio-key",
                "verified-audio-etag"
        );

        assertAll(
                () -> assertArrayEquals(content, result.content()),
                () -> assertEquals("audio/webm", result.contentType())
        );
        ArgumentCaptor<GetObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(requestCaptor.capture());
        GetObjectRequest request = requestCaptor.getValue();
        assertAll(
                () -> assertEquals("techview-audio", request.bucket()),
                () -> assertEquals(
                        "interviews/10/turns/100/audio-key",
                        request.key()
                ),
                () -> assertEquals("verified-audio-etag", request.ifMatch())
        );
    }

    @Test
    @DisplayName("음성 객체가 없으면 객체 없음 오류로 변환한다")
    void shouldMapNotFoundWhenDownloadingAudioObject() {
        assertDownloadFailure(404, AudioStorageFailure.OBJECT_NOT_FOUND);
    }

    @Test
    @DisplayName("음성 객체 ETag가 다르면 버전 불일치 오류로 변환한다")
    void shouldMapVersionMismatchWhenDownloadingAudioObject() {
        assertDownloadFailure(412, AudioStorageFailure.VERSION_MISMATCH);
    }

    @Test
    @DisplayName("음성 다운로드의 일시적 저장소 오류를 재시도 가능한 오류로 변환한다")
    void shouldMapTemporaryFailureWhenDownloadingAudioObject() {
        assertDownloadFailure(503, AudioStorageFailure.TEMPORARY_FAILURE);
    }

    @Test
    @DisplayName("음성 객체 삭제 요청에 버킷과 객체 키 및 검증된 ETag를 전달한다")
    void shouldDeleteAudioObject() {
        audioStorage.delete(
                "interviews/10/turns/100/audio-key",
                "verified-audio-etag"
        );

        ArgumentCaptor<DeleteObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        DeleteObjectRequest request = requestCaptor.getValue();
        assertAll(
                () -> assertEquals("techview-audio", request.bucket()),
                () -> assertEquals(
                        "interviews/10/turns/100/audio-key",
                        request.key()
                ),
                () -> assertEquals("verified-audio-etag", request.ifMatch())
        );
    }

    @Test
    @DisplayName("이미 삭제된 음성 객체를 다시 삭제해도 성공으로 처리한다")
    void shouldTreatMissingAudioObjectDeletionAsSuccess() {
        S3Exception notFound = mock(S3Exception.class);
        when(notFound.statusCode()).thenReturn(404);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(notFound);

        assertDoesNotThrow(
                () -> audioStorage.delete(
                        "interviews/10/turns/100/missing-audio-key",
                        "verified-audio-etag"
                )
        );
    }

    @Test
    @DisplayName("음성 삭제의 일시적 저장소 오류를 재시도 가능한 오류로 변환한다")
    void shouldMapTemporaryFailureWhenDeletingAudioObject() {
        assertDeleteFailure(503, AudioStorageFailure.TEMPORARY_FAILURE);
    }

    @Test
    @DisplayName("음성 삭제 대상 ETag가 다르면 버전 불일치 오류로 변환한다")
    void shouldMapVersionMismatchWhenDeletingAudioObject() {
        assertDeleteFailure(412, AudioStorageFailure.VERSION_MISMATCH);
    }

    private void assertDeleteFailure(
            int statusCode,
            AudioStorageFailure expectedFailure
    ) {
        S3Exception storageFailure = mock(S3Exception.class);
        when(storageFailure.statusCode()).thenReturn(statusCode);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(storageFailure);

        AudioStorageException exception = assertThrows(
                AudioStorageException.class,
                () -> audioStorage.delete(
                        "interviews/10/turns/100/audio-key",
                        "verified-audio-etag"
                )
        );

        assertAll(
                () -> assertSame(expectedFailure, exception.getFailure()),
                () -> assertSame(storageFailure, exception.getCause())
        );
    }

    private void assertDownloadFailure(
            int statusCode,
            AudioStorageFailure expectedFailure
    ) {
        S3Exception storageFailure = mock(S3Exception.class);
        when(storageFailure.statusCode()).thenReturn(statusCode);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(storageFailure);

        AudioStorageException exception = assertThrows(
                AudioStorageException.class,
                () -> audioStorage.download(
                        "interviews/10/turns/100/audio-key",
                        "verified-audio-etag"
                )
        );

        assertAll(
                () -> assertSame(expectedFailure, exception.getFailure()),
                () -> assertSame(storageFailure, exception.getCause())
        );
    }
}
