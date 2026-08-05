package com.ward.techview.interview.scheduler;

import com.ward.techview.interview.service.ExpiredInterviewCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpiredInterviewCleanupSchedulerTest {

    @Test
    @DisplayName("스케줄 실행 시 만료 면접 정리 서비스를 호출한다")
    void delegatesCleanupToService() {
        ExpiredInterviewCleanupService service = mock(ExpiredInterviewCleanupService.class);
        ExpiredInterviewCleanupScheduler scheduler = new ExpiredInterviewCleanupScheduler(service);

        scheduler.cleanup();

        verify(service).cleanup();
    }

    @Test
    @DisplayName("만료 면접 정리 예외가 발생해도 스케줄러 호출을 종료한다")
    void handlesCleanupFailure() {
        ExpiredInterviewCleanupService service = mock(ExpiredInterviewCleanupService.class);
        ExpiredInterviewCleanupScheduler scheduler = new ExpiredInterviewCleanupScheduler(service);
        when(service.cleanup()).thenThrow(new RuntimeException("정리 실패"));

        assertDoesNotThrow(scheduler::cleanup);
    }
}
