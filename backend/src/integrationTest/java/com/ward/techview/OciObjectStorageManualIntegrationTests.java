package com.ward.techview;

import com.ward.techview.integration.storage.AudioStorageConfig;
import com.ward.techview.integration.storage.AudioStorageProperties;
import com.ward.techview.integration.storage.S3CompatibleAudioStorage;
import com.ward.techview.voice.api.storage.AudioObject;
import com.ward.techview.voice.api.storage.AudioObjectMetadata;
import com.ward.techview.voice.api.storage.AudioStorageException;
import com.ward.techview.voice.api.storage.AudioStorageFailure;
import com.ward.techview.voice.api.storage.PresignedUpload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "OCI_STORAGE_MANUAL_TEST", matches = "true")
class OciObjectStorageManualIntegrationTests {

    private static final String CONTENT_TYPE = "audio/webm";
    private static final byte[] AUDIO_CONTENT = "techview-oci-audio-test".getBytes();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private S3Presigner s3Presigner;
    private S3Client s3Client;
    private S3CompatibleAudioStorage audioStorage;
    private String objectKey;
    private String uploadedEtag;

    @BeforeEach
    void setUp() {
        AudioStorageProperties properties = new AudioStorageProperties(
                requiredEnvironmentVariable("OCI_STORAGE_BUCKET"),
                requiredEnvironmentVariable("OCI_STORAGE_REGION"),
                URI.create(requiredEnvironmentVariable("OCI_STORAGE_ENDPOINT")),
                requiredEnvironmentVariable("OCI_STORAGE_ACCESS_KEY"),
                requiredEnvironmentVariable("OCI_STORAGE_SECRET_KEY"),
                Duration.ofMinutes(5),
                true
        );
        AudioStorageConfig config = new AudioStorageConfig();
        s3Presigner = config.s3Presigner(properties);
        s3Client = config.s3Client(properties);
        audioStorage = new S3CompatibleAudioStorage(s3Presigner, s3Client, properties);
        objectKey = "manual-tests/" + UUID.randomUUID() + ".webm";
    }

    @AfterEach
    void tearDown() {
        if (audioStorage != null) {
            try {
                String cleanupEtag = uploadedEtag;
                if (cleanupEtag == null) {
                    cleanupEtag = audioStorage.findMetadata(objectKey)
                            .map(AudioObjectMetadata::etag)
                            .orElse(null);
                }
                if (cleanupEtag != null) {
                    audioStorage.delete(objectKey, cleanupEtag);
                }
            } catch (RuntimeException ignored) {
                // 본 검증 실패를 가리지 않도록 사후 정리 실패는 무시한다.
            }
        }
        if (s3Client != null) {
            s3Client.close();
        }
        if (s3Presigner != null) {
            s3Presigner.close();
        }
    }

    @Test
    @DisplayName("OCI Presigned PUT과 CORS 및 ETag 조건부 처리를 실제 저장소에서 검증한다")
    void shouldUploadAndProcessAudioWithOciS3Compatibility() throws Exception {
        PresignedUpload upload = audioStorage.issueUploadUrl(objectKey, CONTENT_TYPE);

        String requestOrigin = requiredEnvironmentVariable("OCI_STORAGE_ALLOWED_ORIGIN");
        HttpResponse<Void> preflightResponse = sendPreflight(
                upload.uploadUrl(),
                requestOrigin
        );
        assertSuccessful(preflightResponse.statusCode());
        assertPreflightAllowed(preflightResponse, requestOrigin);

        HttpRequest uploadRequest = HttpRequest.newBuilder(upload.uploadUrl())
                .header("Content-Type", CONTENT_TYPE)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(AUDIO_CONTENT))
                .build();
        HttpResponse<Void> uploadResponse = httpClient.send(
                uploadRequest,
                HttpResponse.BodyHandlers.discarding()
        );
        assertSuccessful(uploadResponse.statusCode());

        AudioObjectMetadata metadata = audioStorage.findMetadata(objectKey).orElseThrow();
        uploadedEtag = metadata.etag();
        assertEquals(CONTENT_TYPE, metadata.contentType());
        assertEquals(AUDIO_CONTENT.length, metadata.contentLength());

        AudioObject downloaded = audioStorage.download(objectKey, uploadedEtag);
        assertArrayEquals(AUDIO_CONTENT, downloaded.content());

        AudioStorageException mismatch = assertThrows(
                AudioStorageException.class,
                () -> audioStorage.download(objectKey, "different-etag")
        );
        assertEquals(AudioStorageFailure.VERSION_MISMATCH, mismatch.getFailure());

        audioStorage.delete(objectKey, uploadedEtag);
        uploadedEtag = null;
        Optional<AudioObjectMetadata> deleted = audioStorage.findMetadata(objectKey);
        assertTrue(deleted.isEmpty());
    }

    private HttpResponse<Void> sendPreflight(
            URI uploadUrl,
            String requestOrigin
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uploadUrl)
                .header("Origin", requestOrigin)
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private void assertPreflightAllowed(
            HttpResponse<Void> response,
            String requestOrigin
    ) {
        String allowedOrigin = response.headers()
                .firstValue("access-control-allow-origin")
                .orElseThrow(() -> new AssertionError(
                        "Access-Control-Allow-Origin 응답 헤더가 없습니다."
                ));
        assertTrue(
                allowedOrigin.equals("*") || allowedOrigin.equals(requestOrigin),
                () -> "요청 Origin이 허용되지 않았습니다. allowedOrigin=" + allowedOrigin
        );
        assertTrue(
                headerAllows(response, "access-control-allow-methods", "PUT"),
                "Access-Control-Allow-Methods에 PUT이 없습니다."
        );
        assertTrue(
                headerAllows(response, "access-control-allow-headers", "content-type"),
                "Access-Control-Allow-Headers에 content-type이 없습니다."
        );
    }

    private boolean headerAllows(
            HttpResponse<Void> response,
            String headerName,
            String requiredValue
    ) {
        return response.headers()
                .allValues(headerName)
                .stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .anyMatch(value -> value.equals("*")
                        || value.equalsIgnoreCase(requiredValue));
    }

    private void assertSuccessful(int statusCode) {
        assertTrue(
                statusCode >= 200 && statusCode < 300,
                () -> "OCI Object Storage 요청이 실패했습니다. status=" + statusCode
        );
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경 변수가 필요합니다.");
        }
        return value;
    }
}
