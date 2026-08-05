package com.ward.techview.voice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({VoiceUploadProperties.class, FailedAudioCleanupProperties.class, OrphanAudioCleanupProperties.class})
public class VoiceConfig {
}
