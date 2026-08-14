package com.ward.techview.voice.listener;

import com.ward.techview.processing.api.stt.SttTranscriptionJobQueuedEvent;
import com.ward.techview.voice.service.SttTranscriptionProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig(SttTranscriptionJobQueuedEventDispatchTest.TestConfig.class)
class SttTranscriptionJobQueuedEventDispatchTest {

    private static final Long PROCESSING_JOB_ID = 100L;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SttTranscriptionProcessor processor;

    @Test
    @DisplayName("STT 작업 대기 이벤트는 트랜잭션 커밋 후 비동기로 처리한다")
    void dispatchesEventAsynchronouslyAfterCommit() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(
                    new SttTranscriptionJobQueuedEvent(PROCESSING_JOB_ID)
            );

            verifyNoInteractions(processor);
        });

        verify(processor, timeout(1_000)).process(PROCESSING_JOB_ID);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    @EnableTransactionManagement
    static class TestConfig {

        @Bean("processingTaskExecutor")
        Executor processingTaskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(1);
            executor.setThreadNamePrefix("processing-test-worker-");
            return executor;
        }

        @Bean
        SttTranscriptionProcessor sttTranscriptionProcessor() {
            return mock(SttTranscriptionProcessor.class);
        }

        @Bean
        SttTranscriptionJobQueuedEventListener listener(
                SttTranscriptionProcessor processor
        ) {
            return new SttTranscriptionJobQueuedEventListener(processor);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }
    }

    static class TestTransactionManager
            extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition
        ) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
