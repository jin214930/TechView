package com.ward.techview.account.auth.scheduler;

import com.ward.techview.account.auth.service.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupSchedulerTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private RefreshTokenCleanupScheduler refreshTokenCleanupScheduler;

    @Test
    @DisplayName("스케줄 실행 시 만료·폐기 Refresh Token 정리를 요청한다")
    void shouldRequestExpiredOrRevokedTokenCleanup() {
        when(refreshTokenService.deleteExpiredOrRevoked()).thenReturn(2);

        refreshTokenCleanupScheduler.cleanup();

        verify(refreshTokenService).deleteExpiredOrRevoked();
    }
}
