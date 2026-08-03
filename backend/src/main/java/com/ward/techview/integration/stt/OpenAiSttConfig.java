package com.ward.techview.integration.stt;

import com.ward.techview.voice.api.SpeechToTextProvider;
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
    public RestClient openAiSttRestClient(RestClient.Builder builder, OpenAiSttProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(properties.timeout());

        return builder
                .baseUrl(properties.baseUrl().toString())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public SpeechToTextProvider speechToTextProvider(@Qualifier("openAiSttRestClient") RestClient restClient, OpenAiSttProperties properties) {
        return new OpenAiSpeechToTextProvider(restClient, properties);
    }
}
