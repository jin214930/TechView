package com.ward.techview.account.auth.service;

import com.ward.techview.account.auth.entity.RefreshToken;
import com.ward.techview.account.auth.repository.RefreshTokenRepository;
import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.response.CommonResponseCode;
import com.ward.techview.common.security.IssuedToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveOrRotate(Long memberId, String existingToken, IssuedToken newToken) {
        if (existingToken == null || existingToken.isBlank()) {
            save(memberId, newToken);
            return;
        }

        refreshTokenRepository.findByToken(existingToken)
                .ifPresentOrElse(
                        token -> handleExistingToken(memberId, token, newToken),
                        () -> save(memberId, newToken)
                );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        refreshTokenRepository.findByToken(token)
                .ifPresent(refreshToken -> refreshToken.revoke(now()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public RefreshToken getActiveToken(String token, Long memberId) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenForUpdate(token)
                .orElseThrow(this::authenticationRequired);

        if (!refreshToken.isActive(now()) || !refreshToken.getMemberId().equals(memberId)) {
            throw authenticationRequired();
        }

        return refreshToken;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void rotate(RefreshToken token, IssuedToken newToken) {
        token.rotate(newToken.value(), newToken.expiresAt());
    }

    private void handleExistingToken(Long memberId, RefreshToken existingToken, IssuedToken newToken) {
        if (existingToken.getMemberId().equals(memberId)) {
            existingToken.rotate(newToken.value(), newToken.expiresAt());
            return;
        }

        existingToken.revoke(now());
        save(memberId, newToken);
    }

    private void save(Long memberId, IssuedToken token) {
        refreshTokenRepository.save(RefreshToken.create(token.value(), memberId, token.expiresAt()));
    }

    private BusinessException authenticationRequired() {
        return new BusinessException(CommonResponseCode.AUTHENTICATION_REQUIRED);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
