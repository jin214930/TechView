package com.ward.techview.account.auth.service;

import com.ward.techview.account.auth.response.AuthResponseCode;
import com.ward.techview.account.auth.dto.SignUpRequest;
import com.ward.techview.account.member.entity.Member;
import com.ward.techview.account.member.repository.MemberRepository;
import com.ward.techview.common.exception.BusinessException;
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
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(SignUpRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);

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
}
