package com.ward.techview.processing.scheduler;

import com.ward.techview.processing.service.ProcessingJobPollingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingJobPollingScheduler {
    private final ProcessingJobPollingService pollingService;

    @Scheduled(fixedDelayString = "${custom.processing.worker.polling-delay}")
    public void poll() {
        try {
            int dispatchedCount = pollingService.pollAndPublish();

            if (dispatchedCount > 0) {
                log.info("처리 대기 작업 polling 완료: dispatchedCount = {}", dispatchedCount);
            }
        } catch (RuntimeException e) {
            log.error("처리 작업 polling 중 오류가 발생했습니다.", e);
        }
    }
}
