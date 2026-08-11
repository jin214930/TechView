package com.ward.techview.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MigrationOnlyConfigTest {

    @Test
    @DisplayName("마이그레이션이 완료되면 애플리케이션 컨텍스트를 종료한다")
    void closeApplicationContextAfterMigration() throws Exception {
        ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);
        MigrationOnlyConfig config = new MigrationOnlyConfig();

        ApplicationRunner runner = config.closeAfterMigration(applicationContext);
        runner.run(null);

        verify(applicationContext).close();
    }
}
