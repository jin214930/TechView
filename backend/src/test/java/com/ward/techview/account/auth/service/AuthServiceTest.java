package com.ward.techview.account.auth.service;

import com.ward.techview.account.auth.dto.SignUpRequest;
import com.ward.techview.account.auth.response.AuthResponseCode;
import com.ward.techview.account.member.entity.Member;
import com.ward.techview.account.member.repository.MemberRepository;
import com.ward.techview.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
