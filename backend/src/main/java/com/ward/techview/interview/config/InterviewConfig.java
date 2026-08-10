package com.ward.techview.interview.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExpiredInterviewCleanupProperties.class)
public class InterviewConfig {
    @Bean
    public Clock interviewClock() {
        return Clock.systemUTC();
    }
}
