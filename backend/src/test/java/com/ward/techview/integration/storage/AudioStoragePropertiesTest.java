package com.ward.techview.integration.storage;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioStoragePropertiesTest {

    private static final URI ENDPOINT = URI.create("http://localhost:9000");

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    @DisplayName("음성 저장소 엔드포인트가 없으면 설정 검증에 실패한다")
    void shouldRejectMissingEndpoint() {
        AudioStorageProperties properties = properties(
                null,
                "access-key",
                "secret-key"
        );

        assertViolationOn(properties, "endpoint");
    }

    @Test
    @DisplayName("음성 저장소 Access Key가 비어 있으면 설정 검증에 실패한다")
    void shouldRejectBlankAccessKey() {
        AudioStorageProperties properties = properties(
                ENDPOINT,
                " ",
                "secret-key"
        );

        assertViolationOn(properties, "accessKey");
    }

    @Test
    @DisplayName("음성 저장소 Secret Key가 비어 있으면 설정 검증에 실패한다")
    void shouldRejectBlankSecretKey() {
        AudioStorageProperties properties = properties(
                ENDPOINT,
                "access-key",
                " "
        );

        assertViolationOn(properties, "secretKey");
    }

    @Test
    @DisplayName("필수 음성 저장소 설정이 모두 있으면 검증을 통과한다")
    void shouldAcceptCompleteStorageConfiguration() {
        AudioStorageProperties properties = properties(
                ENDPOINT,
                "access-key",
                "secret-key"
        );

        assertTrue(validator.validate(properties).isEmpty());
    }

    private AudioStorageProperties properties(
            URI endpoint,
            String accessKey,
            String secretKey
    ) {
        return new AudioStorageProperties(
                "techview-audio",
                "ap-northeast-2",
                endpoint,
                accessKey,
                secretKey,
                Duration.ofMinutes(5),
                true
        );
    }

    private void assertViolationOn(
            AudioStorageProperties properties,
            String propertyName
    ) {
        assertTrue(validator.validate(properties)
                .stream()
                .anyMatch(violation -> violation
                        .getPropertyPath()
                        .toString()
                        .equals(propertyName)));
    }
}
