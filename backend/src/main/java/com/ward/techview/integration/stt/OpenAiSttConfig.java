package com.ward.techview.integration.stt;

import com.ward.techview.integration.openai.OpenAiClientProperties;
import com.ward.techview.voice.api.stt.SpeechToTextProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenAiSttProperties.class)
public class OpenAiSttConfig {
    @Bean
    public RestClient openAiSttRestClient(
            RestClient.Builder builder,
            OpenAiClientProperties clientProperties,
            OpenAiSttProperties sttProperties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(sttProperties.timeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(sttProperties.timeout());

        return builder
                .baseUrl(clientProperties.baseUrl().toString())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + clientProperties.apiKey())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public SpeechToTextProvider speechToTextProvider(@Qualifier("openAiSttRestClient") RestClient restClient, OpenAiSttProperties properties) {
        return new OpenAiSpeechToTextProvider(restClient, properties);
    }
}
