package com.ward.techview.account.auth.service;

import com.ward.techview.account.auth.entity.RefreshToken;
import com.ward.techview.account.auth.repository.RefreshTokenRepository;
import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.response.CommonResponseCode;
import com.ward.techview.common.security.IssuedToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    @DisplayName("기존 Refresh Token이 없으면 새 토큰을 저장한다")
    void shouldSaveNewTokenWhenExistingTokenIsMissing() {
        IssuedToken newToken = issuedToken("new-refresh-token");
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);

        refreshTokenService.saveOrRotate(MEMBER_ID, null, newToken);

        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();
        assertAll(
                () -> assertEquals("new-refresh-token", savedToken.getToken()),
                () -> assertEquals(MEMBER_ID, savedToken.getMemberId()),
                () -> assertEquals(newToken.expiresAt(), savedToken.getExpiresAt()),
                () -> assertNull(savedToken.getRevokedAt())
        );
    }

    @Test
    @DisplayName("같은 회원의 기존 Refresh Token이면 해당 행을 회전한다")
    void shouldRotateExistingTokenForSameMember() {
        RefreshToken existingToken = token("existing-token", MEMBER_ID, future());
        existingToken.revoke(OffsetDateTime.now(ZoneOffset.UTC));
        IssuedToken newToken = issuedToken("new-refresh-token");
        when(refreshTokenRepository.findByToken("existing-token"))
                .thenReturn(Optional.of(existingToken));

        refreshTokenService.saveOrRotate(MEMBER_ID, "existing-token", newToken);

        assertAll(
                () -> assertEquals("new-refresh-token", existingToken.getToken()),
                () -> assertEquals(newToken.expiresAt(), existingToken.getExpiresAt()),
                () -> assertNull(existingToken.getRevokedAt())
        );
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 회원의 기존 Refresh Token이면 폐기하고 새 행을 저장한다")
    void shouldRevokeExistingTokenAndSaveNewTokenForDifferentMember() {
        RefreshToken otherMemberToken = token("other-member-token", 2L, future());
        IssuedToken newToken = issuedToken("new-refresh-token");
        when(refreshTokenRepository.findByToken("other-member-token"))
                .thenReturn(Optional.of(otherMemberToken));

        refreshTokenService.saveOrRotate(MEMBER_ID, "other-member-token", newToken);

        assertNotNull(otherMemberToken.getRevokedAt());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("전달된 Refresh Token이 DB에 없으면 새 행을 저장한다")
    void shouldSaveNewTokenWhenExistingTokenIsNotFound() {
        when(refreshTokenRepository.findByToken("unknown-token"))
                .thenReturn(Optional.empty());

        refreshTokenService.saveOrRotate(
                MEMBER_ID,
                "unknown-token",
                issuedToken("new-refresh-token")
        );

        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("로그아웃 토큰이 존재하면 해당 Refresh Token을 폐기한다")
    void shouldRevokeRefreshTokenWhenTokenExists() {
        RefreshToken refreshToken = token("refresh-token", MEMBER_ID, future());
        when(refreshTokenRepository.findByToken("refresh-token"))
                .thenReturn(Optional.of(refreshToken));

        refreshTokenService.revoke("refresh-token");

        assertNotNull(refreshToken.getRevokedAt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("로그아웃 토큰이 없거나 비어 있으면 저장소를 조회하지 않는다")
    void shouldNotQueryRepositoryWhenRevokeTokenIsBlank(String token) {
        refreshTokenService.revoke(token);

        verify(refreshTokenRepository, never()).findByToken(any());
    }

    @Test
    @DisplayName("활성 상태이고 회원이 일치하는 Refresh Token을 반환한다")
    void shouldReturnActiveTokenForMatchingMember() {
        RefreshToken refreshToken = token("refresh-token", MEMBER_ID, future());
        when(refreshTokenRepository.findByToken("refresh-token"))
                .thenReturn(Optional.of(refreshToken));

        RefreshToken result = refreshTokenService.getActiveToken("refresh-token", MEMBER_ID);

        assertSame(refreshToken, result);
    }

    @Test
    @DisplayName("Refresh Token이 DB에 없으면 인증 필요 예외를 발생시킨다")
    void shouldThrowAuthenticationRequiredWhenTokenIsNotFound() {
        when(refreshTokenRepository.findByToken("unknown-token"))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> refreshTokenService.getActiveToken("unknown-token", MEMBER_ID)
        );

        assertSame(CommonResponseCode.AUTHENTICATION_REQUIRED, exception.getResponseCode());
    }

    @Test
    @DisplayName("폐기된 Refresh Token이면 인증 필요 예외를 발생시킨다")
    void shouldThrowAuthenticationRequiredWhenTokenIsRevoked() {
        RefreshToken refreshToken = token("refresh-token", MEMBER_ID, future());
        refreshToken.revoke(OffsetDateTime.now(ZoneOffset.UTC));
        when(refreshTokenRepository.findByToken("refresh-token"))
                .thenReturn(Optional.of(refreshToken));

        assertAuthenticationRequired("refresh-token", MEMBER_ID);
    }

    @Test
    @DisplayName("만료된 Refresh Token이면 인증 필요 예외를 발생시킨다")
    void shouldThrowAuthenticationRequiredWhenTokenIsExpired() {
        RefreshToken refreshToken = token(
                "refresh-token",
                MEMBER_ID,
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1)
        );
        when(refreshTokenRepository.findByToken("refresh-token"))
                .thenReturn(Optional.of(refreshToken));

        assertAuthenticationRequired("refresh-token", MEMBER_ID);
    }

    @Test
    @DisplayName("Refresh Token 회원이 일치하지 않으면 인증 필요 예외를 발생시킨다")
    void shouldThrowAuthenticationRequiredWhenMemberDoesNotMatch() {
        RefreshToken refreshToken = token("refresh-token", 2L, future());
        when(refreshTokenRepository.findByToken("refresh-token"))
                .thenReturn(Optional.of(refreshToken));

        assertAuthenticationRequired("refresh-token", MEMBER_ID);
    }

    @Test
    @DisplayName("재발급된 Refresh Token으로 기존 행을 회전한다")
    void shouldRotateTokenWithReissuedValueAndExpiration() {
        RefreshToken refreshToken = token("existing-token", MEMBER_ID, future());
        IssuedToken newToken = issuedToken("new-refresh-token");

        refreshTokenService.rotate(refreshToken, newToken);

        assertAll(
                () -> assertEquals("new-refresh-token", refreshToken.getToken()),
                () -> assertEquals(newToken.expiresAt(), refreshToken.getExpiresAt()),
                () -> assertNull(refreshToken.getRevokedAt())
        );
    }

    private void assertAuthenticationRequired(String token, Long memberId) {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> refreshTokenService.getActiveToken(token, memberId)
        );

        assertSame(CommonResponseCode.AUTHENTICATION_REQUIRED, exception.getResponseCode());
    }

    private static RefreshToken token(String value, Long memberId, OffsetDateTime expiresAt) {
        return RefreshToken.create(value, memberId, expiresAt);
    }

    private static IssuedToken issuedToken(String value) {
        return new IssuedToken(value, future());
    }

    private static OffsetDateTime future() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
    }
}
