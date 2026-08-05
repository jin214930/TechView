package com.ward.techview.voice.scheduler;

import com.ward.techview.voice.service.FailedAudioCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailedAudioCleanupScheduler {
    private final FailedAudioCleanupService cleanupService;

    @Scheduled(
            cron = "${custom.voice.failed-audio-cleanup.cron}",
            zone = "${custom.voice.failed-audio-cleanup.time-zone}"
    )
    public void cleanup() {
        try {
            int deletedCount = cleanupService.cleanup();

            log.info("실패 음성 정리 완료: deletedCount={}", deletedCount);
        } catch (RuntimeException exception) {
            log.error("실패 음성 정리 작업 중 오류가 발생했습니다.", exception);
        }
    }
}
