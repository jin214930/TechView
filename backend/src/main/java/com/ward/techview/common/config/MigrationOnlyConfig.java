package com.ward.techview.common.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 운영 이미지에 포함된 Flyway와 migration 파일을 그대로 사용한 뒤 애플리케이션을 종료한다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("migration")
public class MigrationOnlyConfig {

    @Bean
    ApplicationRunner closeAfterMigration(ConfigurableApplicationContext applicationContext) {
        return arguments -> applicationContext.close();
    }
}
