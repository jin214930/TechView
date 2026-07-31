package com.ward.techview.account.member.service;

import com.ward.techview.account.member.entity.Member;
import com.ward.techview.account.member.repository.MemberRepository;
import com.ward.techview.account.member.response.MemberResponseCode;
import com.ward.techview.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberLockServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberLockService memberLockService;

    @Test
    @DisplayName("회원이 존재하면 비관적 락 조회에 성공한다")
    void shouldLockMemberWhenMemberExists() {
        Member member = Member.create("user@example.com", "encoded-password", "사용자");
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        assertDoesNotThrow(() -> memberLockService.lock(MEMBER_ID));

        verify(memberRepository).findByIdForUpdate(MEMBER_ID);
    }

    @Test
    @DisplayName("락을 획득할 회원이 없으면 회원 없음 예외를 발생시킨다")
    void shouldThrowMemberNotFoundWhenLockingMissingMember() {
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberLockService.lock(MEMBER_ID)
        );

        assertSame(MemberResponseCode.MEMBER_NOT_FOUND, exception.getResponseCode());
    }
}
