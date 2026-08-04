package com.ward.techview.integration.analysis;

import com.ward.techview.evaluation.api.AnswerAnalysisProvider;
import com.ward.techview.integration.openai.OpenAiClientProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenAiAnswerAnalysisProperties.class)
public class OpenAiAnswerAnalysisConfig {
    @Bean
    public RestClient openAiAnswerAnalysisRestClient(
            RestClient.Builder builder,
            OpenAiClientProperties clientProperties,
            OpenAiAnswerAnalysisProperties analysisProperties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(analysisProperties.timeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(analysisProperties.timeout());

        return builder
                .baseUrl(clientProperties.baseUrl().toString())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + clientProperties.apiKey())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public AnswerAnalysisProvider answerAnalysisProvider(
            @Qualifier("openAiAnswerAnalysisRestClient")
            RestClient restClient,
            ObjectMapper objectMapper,
            OpenAiAnswerAnalysisProperties properties
    ) {
        return new OpenAiAnswerAnalysisProvider(
                restClient,
                objectMapper,
                properties
        );
    }
}
