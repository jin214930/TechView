package com.ward.techview.account.auth.service;

import com.ward.techview.account.auth.dto.*;
import com.ward.techview.account.auth.entity.RefreshToken;
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

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberRepository memberRepository;
    private final RefreshTokenService refreshTokenService;
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

        refreshTokenService.saveOrRotate(member.getId(), existingRefreshToken, refreshToken);

        return new LoginResult(refreshToken.value(), LoginResponse.of(accessToken, member));
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional
    public TokenReissueResult reissueTokens(String refreshToken) {
        CustomUserDetails principal = jwtProvider.getRefreshTokenPrincipal(refreshToken);
        if (principal == null) {
            throw new BusinessException(CommonResponseCode.AUTHENTICATION_REQUIRED);
        }

        RefreshToken refreshTokenEntity = refreshTokenService.getActiveToken(refreshToken, principal.id());

        Member member = memberRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException(CommonResponseCode.AUTHENTICATION_REQUIRED));

        IssuedToken accessToken = jwtProvider.generateAccessToken(member.getId(), member.getEmail());
        IssuedToken newRefreshToken = jwtProvider.generateRefreshToken(member.getId(), member.getEmail());

        refreshTokenService.rotate(refreshTokenEntity, newRefreshToken);

        return new TokenReissueResult(newRefreshToken.value(), new TokenResponse(accessToken.value()));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

}
