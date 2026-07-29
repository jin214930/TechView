package com.ward.techview.account.auth.service;

import com.ward.techview.account.auth.dto.LoginRequest;
import com.ward.techview.account.auth.dto.LoginResult;
import com.ward.techview.account.auth.dto.SignUpRequest;
import com.ward.techview.account.auth.entity.RefreshToken;
import com.ward.techview.account.auth.repository.RefreshTokenRepository;
import com.ward.techview.account.auth.response.AuthResponseCode;
import com.ward.techview.account.member.entity.Member;
import com.ward.techview.account.member.repository.MemberRepository;
import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.security.IssuedToken;
import com.ward.techview.common.security.JwtProvider;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("회원가입 시 이메일을 정규화하고 암호화된 비밀번호로 회원을 저장한다")
    void shouldNormalizeEmailAndSaveMemberWithEncodedPassword() {
        SignUpRequest request = new SignUpRequest("User@Example.com", "password123!", "사용자");
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);

        authService.signUp(request);

        verify(memberRepository).existsByEmail("user@example.com");
        verify(passwordEncoder).encode("password123!");
        verify(memberRepository).save(memberCaptor.capture());

        Member savedMember = memberCaptor.getValue();
        assertEquals("user@example.com", savedMember.getEmail());
        assertEquals("encoded-password", savedMember.getPassword());
        assertEquals("사용자", savedMember.getNickname());
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 이메일 중복 예외를 발생시킨다")
    void shouldThrowEmailConflictWhenEmailAlreadyExists() {
        SignUpRequest request = new SignUpRequest("User@Example.com", "password123!", "사용자");
        when(memberRepository.existsByEmail("user@example.com")).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.signUp(request)
        );

        assertSame(AuthResponseCode.EMAIL_CONFLICT, exception.getResponseCode());
        verify(passwordEncoder, never()).encode(any());
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("회원 저장 중 데이터 무결성 충돌이 발생하면 이메일 중복 예외로 변환한다")
    void shouldTranslateDataIntegrityViolationToEmailConflict() {
        SignUpRequest request = new SignUpRequest("user@example.com", "password123!", "사용자");
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.signUp(request)
        );

        assertSame(AuthResponseCode.EMAIL_CONFLICT, exception.getResponseCode());
    }

    @Test
    @DisplayName("로그인에 성공하면 이메일을 정규화하고 토큰과 회원 정보를 반환한다")
    void shouldNormalizeEmailAndReturnTokensWhenLoginSucceeds() {
        Member member = member(1L);
        LoginRequest request = new LoginRequest(" User@Example.com ", "password123!");
        IssuedToken accessToken = accessToken();
        IssuedToken refreshToken = refreshToken();
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);

        when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("password123!", member.getPassword())).thenReturn(true);
        when(jwtProvider.generateAccessToken(1L, "user@example.com")).thenReturn(accessToken);
        when(jwtProvider.generateRefreshToken(1L, "user@example.com")).thenReturn(refreshToken);

        LoginResult result = authService.login(request, null);

        assertAll(
                () -> assertEquals("refresh-token", result.refreshToken()),
                () -> assertEquals("access-token", result.loginResponse().accessToken()),
                () -> assertEquals(1L, result.loginResponse().memberId()),
                () -> assertEquals("user@example.com", result.loginResponse().email()),
                () -> assertEquals("사용자", result.loginResponse().nickname())
        );
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();
        assertAll(
                () -> assertEquals("refresh-token", savedToken.getToken()),
                () -> assertEquals(1L, savedToken.getMemberId()),
                () -> assertEquals(refreshToken.expiresAt(), savedToken.getExpiresAt()),
                () -> assertNull(savedToken.getRevokedAt())
        );
    }

    @Test
    @DisplayName("회원이 존재하지 않으면 로그인 실패 예외를 발생시킨다")
    void shouldThrowLoginFailedWhenMemberDoesNotExist() {
        LoginRequest request = new LoginRequest("user@example.com", "password123!");
        when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(request, null)
        );

        assertSame(AuthResponseCode.LOGIN_FAILED, exception.getResponseCode());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtProvider, never()).generateAccessToken(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 로그인 실패 예외를 발생시킨다")
    void shouldThrowLoginFailedWhenPasswordDoesNotMatch() {
        Member member = member(1L);
        LoginRequest request = new LoginRequest("user@example.com", "wrong-password");
        when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong-password", member.getPassword())).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(request, null)
        );

        assertSame(AuthResponseCode.LOGIN_FAILED, exception.getResponseCode());
        verify(jwtProvider, never()).generateAccessToken(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 회원의 Refresh Token이 있으면 기존 행을 새 토큰으로 회전한다")
    void shouldRotateExistingRefreshTokenForSameMember() {
        Member member = member(1L);
        RefreshToken existingToken = RefreshToken.create(
                "existing-refresh-token",
                1L,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
        );
        existingToken.revoke(OffsetDateTime.now(ZoneOffset.UTC));
        stubSuccessfulLogin(member);
        when(refreshTokenRepository.findByToken("existing-refresh-token"))
                .thenReturn(Optional.of(existingToken));

        authService.login(
                new LoginRequest("user@example.com", "password123!"),
                "existing-refresh-token"
        );

        assertAll(
                () -> assertEquals("refresh-token", existingToken.getToken()),
                () -> assertEquals(refreshToken().expiresAt(), existingToken.getExpiresAt()),
                () -> assertNull(existingToken.getRevokedAt())
        );
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 회원의 Refresh Token이면 기존 토큰을 폐기하고 새 행을 저장한다")
    void shouldRevokeExistingTokenAndSaveNewTokenForDifferentMember() {
        Member member = member(1L);
        RefreshToken otherMemberToken = RefreshToken.create(
                "other-member-token",
                2L,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)
        );
        stubSuccessfulLogin(member);
        when(refreshTokenRepository.findByToken("other-member-token"))
                .thenReturn(Optional.of(otherMemberToken));

        authService.login(
                new LoginRequest("user@example.com", "password123!"),
                "other-member-token"
        );

        assertNotNull(otherMemberToken.getRevokedAt());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("전달된 Refresh Token이 DB에 없으면 새 행을 저장한다")
    void shouldSaveNewRefreshTokenWhenExistingTokenIsNotFound() {
        Member member = member(1L);
        stubSuccessfulLogin(member);
        when(refreshTokenRepository.findByToken("unknown-token")).thenReturn(Optional.empty());

        authService.login(
                new LoginRequest("user@example.com", "password123!"),
                "unknown-token"
        );

        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("로그아웃 시 Refresh Token이 존재하면 해당 토큰을 폐기한다")
    void shouldRevokeRefreshTokenWhenLogoutTokenExists() {
        RefreshToken refreshToken = RefreshToken.create(
                "refresh-token",
                1L,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)
        );
        when(refreshTokenRepository.findByToken("refresh-token"))
                .thenReturn(Optional.of(refreshToken));

        authService.logout("refresh-token");

        assertNotNull(refreshToken.getRevokedAt());
        verify(refreshTokenRepository).findByToken("refresh-token");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("로그아웃 토큰이 없거나 비어 있으면 저장소를 조회하지 않는다")
    void shouldNotQueryRepositoryWhenLogoutTokenIsBlank(String refreshToken) {
        authService.logout(refreshToken);

        verify(refreshTokenRepository, never()).findByToken(any());
    }

    @Test
    @DisplayName("로그아웃 토큰이 DB에 없어도 예외 없이 완료한다")
    void shouldCompleteLogoutWhenRefreshTokenIsNotFound() {
        when(refreshTokenRepository.findByToken("unknown-token"))
                .thenReturn(Optional.empty());

        authService.logout("unknown-token");

        verify(refreshTokenRepository).findByToken("unknown-token");
    }

    private void stubSuccessfulLogin(Member member) {
        when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("password123!", member.getPassword())).thenReturn(true);
        when(jwtProvider.generateAccessToken(member.getId(), member.getEmail())).thenReturn(accessToken());
        when(jwtProvider.generateRefreshToken(member.getId(), member.getEmail())).thenReturn(refreshToken());
    }

    private static Member member(Long id) {
        Member member = Member.create("user@example.com", "encoded-password", "사용자");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static IssuedToken accessToken() {
        return new IssuedToken(
                "access-token",
                OffsetDateTime.of(2026, 7, 29, 13, 15, 0, 0, ZoneOffset.UTC)
        );
    }

    private static IssuedToken refreshToken() {
        return new IssuedToken(
                "refresh-token",
                OffsetDateTime.of(2026, 8, 28, 13, 0, 0, 0, ZoneOffset.UTC)
        );
    }
}
