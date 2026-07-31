package com.ward.techview.integration.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AudioStorageProperties.class)
public class AudioStorageConfig {

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(
            AudioStorageProperties properties
    ) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(
                                        properties.pathStyleAccessEnabled()
                                )
                                .build()
                );

        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }

        return builder.build();
    }

    private AwsCredentialsProvider credentialsProvider(
            AudioStorageProperties properties
    ) {
        boolean hasAccessKey =
                StringUtils.hasText(properties.accessKey());
        boolean hasSecretKey =
                StringUtils.hasText(properties.secretKey());

        if (!hasAccessKey && !hasSecretKey) {
            return DefaultCredentialsProvider.builder().build();
        }

        if (!hasAccessKey || !hasSecretKey) {
            throw new IllegalStateException(
                    "저장소 Access Key와 Secret Key를 모두 설정해야 합니다."
            );
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.accessKey(),
                properties.secretKey()
        );

        return StaticCredentialsProvider.create(credentials);
    }
}
