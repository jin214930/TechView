package com.ward.techview.processing.scheduler;

import com.ward.techview.processing.service.ProcessingJobPollingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProcessingJobPollingSchedulerTest {

    @Mock
    private ProcessingJobPollingService pollingService;

    @InjectMocks
    private ProcessingJobPollingScheduler scheduler;

    @Test
    @DisplayName("주기적으로 처리 대기 작업 조회와 이벤트 발행을 요청한다")
    void requestsJobPolling() {
        scheduler.poll();

        verify(pollingService).pollAndPublish();
    }

    @Test
    @DisplayName("polling 오류가 발생해도 스케줄러 밖으로 전파하지 않는다")
    void catchesPollingFailure() {
        doThrow(new IllegalStateException("polling 오류"))
                .when(pollingService)
                .pollAndPublish();

        assertDoesNotThrow(scheduler::poll);
        verify(pollingService).pollAndPublish();
    }
}
