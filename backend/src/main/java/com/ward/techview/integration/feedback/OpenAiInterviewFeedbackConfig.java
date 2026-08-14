package com.ward.techview.integration.feedback;

import com.ward.techview.evaluation.api.feedback.InterviewFeedbackProvider;
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
@EnableConfigurationProperties(OpenAiInterviewFeedbackProperties.class)
public class OpenAiInterviewFeedbackConfig {
    @Bean
    public RestClient openAiInterviewFeedbackRestClient(
            RestClient.Builder builder,
            OpenAiClientProperties clientProperties,
            OpenAiInterviewFeedbackProperties feedbackProperties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(feedbackProperties.timeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(feedbackProperties.timeout());

        return builder
                .baseUrl(clientProperties.baseUrl().toString())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + clientProperties.apiKey())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public InterviewFeedbackProvider interviewFeedbackProvider(
            @Qualifier("openAiInterviewFeedbackRestClient")
            RestClient restClient,
            ObjectMapper objectMapper,
            OpenAiInterviewFeedbackProperties properties
    ) {
        return new OpenAiInterviewFeedbackProvider(restClient, objectMapper, properties);
    }
}
