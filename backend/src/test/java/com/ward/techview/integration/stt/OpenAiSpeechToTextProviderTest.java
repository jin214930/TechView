package com.ward.techview.integration.stt;

import com.ward.techview.voice.api.AudioObject;
import com.ward.techview.voice.api.SpeechToTextException;
import com.ward.techview.voice.api.SpeechToTextFailure;
import com.ward.techview.voice.api.TranscriptionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiSpeechToTextProviderTest {

    private MockRestServiceServer server;
    private OpenAiSpeechToTextProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        OpenAiSttProperties properties = new OpenAiSttProperties(
                URI.create("https://api.openai.com"),
                "test-api-key",
                "gpt-4o-mini-transcribe",
                "ko",
                Duration.ofSeconds(60)
        );
        provider = new OpenAiSpeechToTextProvider(
                builder.baseUrl(properties.baseUrl().toString()).build(),
                properties
        );
    }

    @Test
    @DisplayName("음성 파일과 STT 설정을 multipart 요청으로 전송한다")
    void transcribesAudioUsingMultipartRequest() {
        server.expect(once(), requestTo("https://api.openai.com/v1/audio/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(containsString("name=\"file\"")))
                .andExpect(content().string(containsString("filename=\"audio.webm\"")))
                .andExpect(content().string(containsString("name=\"model\"")))
                .andExpect(content().string(containsString("gpt-4o-mini-transcribe")))
                .andExpect(content().string(containsString("name=\"language\"")))
                .andExpect(content().string(containsString("ko")))
                .andRespond(withSuccess(
                        "{\"text\":\"안녕하세요\"}",
                        MediaType.APPLICATION_JSON
                ));

        TranscriptionResult result = provider.transcribe(
                new AudioObject(new byte[]{1, 2, 3}, "audio/webm")
        );

        assertEquals("안녕하세요", result.transcript());
        server.verify();
    }

    @Test
    @DisplayName("잘못된 음성 요청 응답을 유효하지 않은 음성 오류로 변환한다")
    void mapsBadRequestToInvalidAudio() {
        server.expect(once(), requestTo("https://api.openai.com/v1/audio/transcriptions"))
                .andRespond(withBadRequest());

        SpeechToTextException exception = assertThrows(
                SpeechToTextException.class,
                () -> provider.transcribe(new AudioObject(new byte[]{1}, "audio/webm"))
        );

        assertSame(SpeechToTextFailure.INVALID_AUDIO, exception.getFailure());
        server.verify();
    }

    @Test
    @DisplayName("요청 시간 초과 응답을 일시적 오류로 변환한다")
    void mapsRequestTimeoutToTemporaryFailure() {
        server.expect(once(), requestTo("https://api.openai.com/v1/audio/transcriptions"))
                .andRespond(withRawStatus(408));

        SpeechToTextException exception = assertThrows(
                SpeechToTextException.class,
                () -> provider.transcribe(new AudioObject(new byte[]{1}, "audio/webm"))
        );

        assertSame(SpeechToTextFailure.TEMPORARY_FAILURE, exception.getFailure());
        server.verify();
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type을 유효하지 않은 음성 오류로 변환한다")
    void rejectsUnsupportedContentType() {
        SpeechToTextException exception = assertThrows(
                SpeechToTextException.class,
                () -> provider.transcribe(new AudioObject(new byte[]{1}, "image/webm"))
        );

        assertSame(SpeechToTextFailure.INVALID_AUDIO, exception.getFailure());
        server.verify();
    }

    @Test
    @DisplayName("유효한 텍스트가 없는 STT 응답을 알 수 없는 오류로 변환한다")
    void rejectsBlankTranscriptionResponse() {
        server.expect(once(), requestTo("https://api.openai.com/v1/audio/transcriptions"))
                .andRespond(withSuccess("{\"text\":\" \"}", MediaType.APPLICATION_JSON));

        SpeechToTextException exception = assertThrows(
                SpeechToTextException.class,
                () -> provider.transcribe(new AudioObject(new byte[]{1}, "audio/webm"))
        );

        assertAll(
                () -> assertSame(SpeechToTextFailure.UNKNOWN, exception.getFailure()),
                server::verify
        );
    }
}
