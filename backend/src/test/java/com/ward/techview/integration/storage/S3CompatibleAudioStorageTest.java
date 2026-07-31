package com.ward.techview.integration.storage;

import com.ward.techview.voice.api.PresignedUpload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3CompatibleAudioStorageTest {

    private static final URI ENDPOINT = URI.create("http://localhost:9000");
    private static final Duration EXPIRATION = Duration.ofMinutes(5);

    private S3Presigner s3Presigner;
    private S3CompatibleAudioStorage audioStorage;

    @BeforeEach
    void setUp() {
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
        audioStorage = new S3CompatibleAudioStorage(s3Presigner, properties);
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
