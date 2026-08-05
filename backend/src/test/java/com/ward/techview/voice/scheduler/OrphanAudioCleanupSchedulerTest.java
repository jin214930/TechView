package com.ward.techview.voice.scheduler;

import com.ward.techview.voice.service.OrphanAudioCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrphanAudioCleanupSchedulerTest {

    @Test
    @DisplayName("스케줄 실행 시 고아 음성 정리 서비스를 호출한다")
    void delegatesCleanupToService() {
        OrphanAudioCleanupService service = mock(OrphanAudioCleanupService.class);
        OrphanAudioCleanupScheduler scheduler = new OrphanAudioCleanupScheduler(service);

        scheduler.cleanup();

        verify(service).cleanup();
    }

    @Test
    @DisplayName("정리 작업에서 예외가 발생해도 스케줄러 호출을 종료한다")
    void handlesCleanupFailure() {
        OrphanAudioCleanupService service = mock(OrphanAudioCleanupService.class);
        OrphanAudioCleanupScheduler scheduler = new OrphanAudioCleanupScheduler(service);
        when(service.cleanup()).thenThrow(new RuntimeException("정리 실패"));

        assertDoesNotThrow(scheduler::cleanup);
    }
}
