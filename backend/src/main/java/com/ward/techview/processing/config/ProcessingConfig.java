package com.ward.techview.processing.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProcessingWorkerProperties.class)
public class ProcessingConfig {
}
