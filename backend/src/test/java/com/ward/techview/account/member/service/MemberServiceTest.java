package com.ward.techview.account.member.service;

import com.ward.techview.account.member.dto.MemberResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberService memberService;

    @Test
    @DisplayName("회원이 존재하면 프로필 정보를 반환한다")
    void shouldReturnMemberProfileWhenMemberExists() {
        Member member = member();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        MemberResponse response = memberService.getMember(MEMBER_ID);

        assertAll(
                () -> assertEquals(MEMBER_ID, response.id()),
                () -> assertEquals("user@example.com", response.email()),
                () -> assertEquals("사용자", response.nickname())
        );
    }

    @Test
    @DisplayName("프로필을 조회할 회원이 없으면 회원 없음 예외를 발생시킨다")
    void shouldThrowMemberNotFoundWhenGettingMissingMember() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberService.getMember(MEMBER_ID)
        );

        assertSame(MemberResponseCode.MEMBER_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    @DisplayName("회원이 존재하면 해당 회원을 삭제한다")
    void shouldDeleteMemberWhenMemberExists() {
        Member member = member();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        memberService.deleteMember(MEMBER_ID);

        verify(memberRepository).delete(member);
    }

    @Test
    @DisplayName("탈퇴할 회원이 없으면 회원 없음 예외를 발생시키고 삭제하지 않는다")
    void shouldThrowMemberNotFoundAndNotDeleteWhenMemberIsMissing() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberService.deleteMember(MEMBER_ID)
        );

        assertSame(MemberResponseCode.MEMBER_NOT_FOUND, exception.getResponseCode());
        verify(memberRepository, never()).delete(org.mockito.ArgumentMatchers.any(Member.class));
    }

    private static Member member() {
        Member member = Member.create("user@example.com", "encoded-password", "사용자");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }
}
