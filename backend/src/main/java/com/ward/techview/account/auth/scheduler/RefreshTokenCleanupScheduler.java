package com.ward.techview.account.auth.scheduler;

import com.ward.techview.account.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {
    private final RefreshTokenService refreshTokenService;

    @Scheduled(
            cron = "${custom.auth.refresh-token.cleanup.cron}",
            zone = "${custom.auth.refresh-token.cleanup.time-zone}"
    )
    public void cleanup() {
        int deletedCount = refreshTokenService.deleteExpiredOrRevoked();

        log.info("만료·폐기 Refresh Token 정리 완료: deletedCount={}", deletedCount);
    }
}
