package com.ward.techview.integration.analysis;

import com.ward.techview.evaluation.api.AnswerAnalysisProvider;
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
    public RestClient openAiAnswerAnalysisRestClient(RestClient.Builder builder, OpenAiAnswerAnalysisProperties properties) {
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
