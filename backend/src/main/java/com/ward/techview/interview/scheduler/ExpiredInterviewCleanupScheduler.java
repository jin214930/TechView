package com.ward.techview.interview.scheduler;

import com.ward.techview.interview.service.ExpiredInterviewCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredInterviewCleanupScheduler {
    private final ExpiredInterviewCleanupService cleanupService;

    @Scheduled(
            cron = "${custom.interview.expired-session-cleanup.cron}",
            zone = "${custom.interview.expired-session-cleanup.time-zone}"
    )
    public void cleanup() {
        try {
            int deletedCount = cleanupService.cleanup();

            log.info("만료 면접 세션 정리 완료: deletedCount={}", deletedCount);
        } catch (RuntimeException exception) {
            log.error("만료 면접 세션 정리 중 오류가 발생했습니다.", exception);
        }
    }
}
