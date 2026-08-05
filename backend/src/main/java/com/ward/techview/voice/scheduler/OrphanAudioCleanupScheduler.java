package com.ward.techview.voice.scheduler;

import com.ward.techview.voice.service.OrphanAudioCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanAudioCleanupScheduler {
    private final OrphanAudioCleanupService cleanupService;

    @Scheduled(
            cron = "${custom.voice.orphan-audio-cleanup.cron}",
            zone = "${custom.voice.orphan-audio-cleanup.time-zone}"
    )
    public void cleanup() {
        try {
            int deletedCount = cleanupService.cleanup();

            log.info("고아 음성 객체 정리 완료: deletedCount={}", deletedCount);
        } catch (RuntimeException exception) {
            log.error("고아 음성 객체 정리 작업 중 오류가 발생했습니다.", exception);
        }
    }
}
