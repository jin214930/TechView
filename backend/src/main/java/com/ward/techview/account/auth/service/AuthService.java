package com.ward.techview.account.auth.service;

import com.ward.techview.account.auth.dto.*;
import com.ward.techview.account.auth.entity.RefreshToken;
import com.ward.techview.account.auth.repository.RefreshTokenRepository;
import com.ward.techview.account.auth.response.AuthResponseCode;
import com.ward.techview.account.member.entity.Member;
import com.ward.techview.account.member.repository.MemberRepository;
import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.response.CommonResponseCode;
import com.ward.techview.common.security.CustomUserDetails;
import com.ward.techview.common.security.IssuedToken;
import com.ward.techview.common.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(SignUpRequest request) {
        String email = normalizeEmail(request.email());

        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(AuthResponseCode.EMAIL_CONFLICT);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        Member member = Member.create(email, encodedPassword, request.nickname());

        try {
            memberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AuthResponseCode.EMAIL_CONFLICT);
        }
    }

    @Transactional
    public LoginResult login(LoginRequest request, String existingRefreshToken) {
        String email = normalizeEmail(request.email());

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(AuthResponseCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(AuthResponseCode.LOGIN_FAILED);
        }

        IssuedToken accessToken = jwtProvider.generateAccessToken(member.getId(), member.getEmail());
        IssuedToken refreshToken = jwtProvider.generateRefreshToken(member.getId(), member.getEmail());

        saveOrRotateRefreshToken(member, existingRefreshToken, refreshToken);

        return new LoginResult(refreshToken.value(), LoginResponse.of(accessToken, member));
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(token -> token.revoke(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Transactional
    public TokenReissueResult reissueTokens(String refreshToken) {
        CustomUserDetails principal = jwtProvider.getRefreshTokenPrincipal(refreshToken);
        if (principal == null) {
            throw new BusinessException(CommonResponseCode.AUTHENTICATION_REQUIRED);
        }

        RefreshToken refreshTokenEntity = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BusinessException(CommonResponseCode.AUTHENTICATION_REQUIRED));

        if (!refreshTokenEntity.isActive(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException(CommonResponseCode.AUTHENTICATION_REQUIRED);
        }

        if (!refreshTokenEntity.getMemberId().equals(principal.id())) {
            throw new BusinessException(CommonResponseCode.AUTHENTICATION_REQUIRED);
        }

        Member member = memberRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException(CommonResponseCode.AUTHENTICATION_REQUIRED));

        IssuedToken accessToken = jwtProvider.generateAccessToken(member.getId(), member.getEmail());
        IssuedToken newRefreshToken = jwtProvider.generateRefreshToken(member.getId(), member.getEmail());

        refreshTokenEntity.rotate(newRefreshToken.value(), newRefreshToken.expiresAt());

        return new TokenReissueResult(newRefreshToken.value(), new TokenResponse(accessToken.value()));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void saveOrRotateRefreshToken(Member member, String existingRefreshToken, IssuedToken newToken) {
        if (existingRefreshToken == null || existingRefreshToken.isBlank()) {
            saveRefreshToken(member, newToken);
            return;
        }

        refreshTokenRepository.findByToken(existingRefreshToken)
                .ifPresentOrElse(
                        existingToken -> handleExistingToken(member, existingToken, newToken),
                        () -> saveRefreshToken(member, newToken)
                );
    }

    private void handleExistingToken(Member member, RefreshToken existingToken, IssuedToken newToken) {
        if (existingToken.getMemberId().equals(member.getId())) {
            existingToken.rotate(newToken.value(), newToken.expiresAt());
            return;
        }

        existingToken.revoke(OffsetDateTime.now(ZoneOffset.UTC));
        saveRefreshToken(member, newToken);
    }

    private void saveRefreshToken(Member member, IssuedToken token) {
        refreshTokenRepository.save(RefreshToken.create(token.value(), member.getId(), token.expiresAt()));
    }
}
