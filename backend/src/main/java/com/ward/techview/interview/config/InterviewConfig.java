package com.ward.techview.interview.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExpiredInterviewCleanupProperties.class)
public class InterviewConfig {
}
