package com.ward.techview.account.member.service;

import com.ward.techview.account.api.member.MemberLockManager;
import com.ward.techview.account.member.repository.MemberRepository;
import com.ward.techview.account.member.response.MemberResponseCode;
import com.ward.techview.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberLockService implements MemberLockManager {
    private final MemberRepository memberRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Long memberId) {
        memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(MemberResponseCode.MEMBER_NOT_FOUND));
    }
}
