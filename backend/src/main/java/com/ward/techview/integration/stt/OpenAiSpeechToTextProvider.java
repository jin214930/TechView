package com.ward.techview.integration.stt;

import com.ward.techview.voice.api.AudioObject;
import com.ward.techview.voice.api.SpeechToTextException;
import com.ward.techview.voice.api.SpeechToTextFailure;
import com.ward.techview.voice.api.SpeechToTextProvider;
import com.ward.techview.voice.api.TranscriptionResult;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;
import java.util.Objects;

public class OpenAiSpeechToTextProvider implements SpeechToTextProvider {
    private static final String TRANSCRIPTION_URI = "/v1/audio/transcriptions";

    private final RestClient restClient;
    private final OpenAiSttProperties properties;

    public OpenAiSpeechToTextProvider(RestClient restClient, OpenAiSttProperties properties) {
        this.restClient = Objects.requireNonNull(restClient);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public TranscriptionResult transcribe(AudioObject audioObject) {
        Objects.requireNonNull(audioObject);

        try {
            OpenAiTranscriptionResponse response = restClient.post()
                    .uri(TRANSCRIPTION_URI)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(createRequestBody(audioObject))
                    .retrieve()
                    .body(OpenAiTranscriptionResponse.class);

            return toResult(response);
        } catch (RestClientResponseException exception) {
            throw mapResponseException(exception);
        } catch (ResourceAccessException exception) {
            throw new SpeechToTextException(
                    SpeechToTextFailure.TEMPORARY_FAILURE,
                    "STT 공급자에 연결할 수 없습니다.",
                    exception
            );
        } catch (RestClientException exception) {
            throw new SpeechToTextException(
                    SpeechToTextFailure.UNKNOWN,
                    "STT 응답을 처리할 수 없습니다.",
                    exception
            );
        }
    }

    private MultiValueMap<String, Object> createRequestBody(
            AudioObject audioObject
    ) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        body.add("file", createFilePart(audioObject));
        body.add("model", properties.model());
        body.add("language", properties.language());
        body.add("response_format", "json");

        return body;
    }

    private HttpEntity<ByteArrayResource> createFilePart(
            AudioObject audioObject
    ) {
        MediaType mediaType = parseAudioMediaType(audioObject.contentType());
        String filename = resolveFilename(mediaType, audioObject.contentType());

        ByteArrayResource resource = new ByteArrayResource(audioObject.content()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);

        return new HttpEntity<>(resource, headers);
    }

    private MediaType parseAudioMediaType(String contentType) {
        MediaType mediaType;

        try {
            mediaType = MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            throw invalidAudio("지원하지 않는 음성 Content-Type입니다.", exception);
        }

        if (!"audio".equalsIgnoreCase(mediaType.getType())) {
            throw invalidAudio(
                    "지원하지 않는 음성 Content-Type입니다: " + contentType,
                    null
            );
        }

        return mediaType;
    }

    private String resolveFilename(MediaType mediaType, String contentType) {
        return switch (mediaType.getSubtype().toLowerCase(Locale.ROOT)) {
            case "webm" -> "audio.webm";
            case "mpeg", "mp3" -> "audio.mp3";
            case "mp4", "x-m4a" -> "audio.m4a";
            case "wav", "x-wav" -> "audio.wav";
            case "ogg" -> "audio.ogg";
            case "flac" -> "audio.flac";
            default -> throw invalidAudio("지원하지 않는 음성 Content-Type입니다: " + contentType, null);
        };
    }

    private TranscriptionResult toResult(OpenAiTranscriptionResponse response) {
        if (response == null || response.text() == null || response.text().isBlank()) {
            throw new SpeechToTextException(SpeechToTextFailure.UNKNOWN, "STT 공급자가 유효한 텍스트를 반환하지 않았습니다.");
        }

        return new TranscriptionResult(response.text());
    }

    private SpeechToTextException mapResponseException(RestClientResponseException exception) {
        SpeechToTextFailure failure = switch (exception.getStatusCode().value()) {
            case 400, 413, 415, 422 -> SpeechToTextFailure.INVALID_AUDIO;
            case 401, 403 -> SpeechToTextFailure.AUTHENTICATION_FAILED;
            case 429 -> SpeechToTextFailure.RATE_LIMITED;
            case 408, 409, 500, 502, 503, 504 -> SpeechToTextFailure.TEMPORARY_FAILURE;
            default -> SpeechToTextFailure.UNKNOWN;
        };

        return new SpeechToTextException(
                failure,
                "STT 요청 처리에 실패했습니다. status="
                        + exception.getStatusCode().value(),
                exception
        );
    }

    private SpeechToTextException invalidAudio(String message, Throwable cause) {
        return new SpeechToTextException(
                SpeechToTextFailure.INVALID_AUDIO,
                message,
                cause
        );
    }
}
