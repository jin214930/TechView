package com.ward.techview.account.member.service;

import com.ward.techview.account.member.dto.MemberResponse;
import com.ward.techview.account.member.entity.Member;
import com.ward.techview.account.member.repository.MemberRepository;
import com.ward.techview.account.member.response.MemberResponseCode;
import com.ward.techview.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberResponse getMember(Long id) {
        Member member = getMemberById(id);

        return MemberResponse.from(member);
    }

    @Transactional
    public void deleteMember(Long id) {
        Member member = getMemberById(id);

        // refresh_token은 FK ON DELETE CASCADE로 함께 삭제된다.
        memberRepository.delete(member);
    }

    private Member getMemberById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(MemberResponseCode.MEMBER_NOT_FOUND));
    }
}
