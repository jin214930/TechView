package com.ward.techview.account.member.dto;

import com.ward.techview.account.member.entity.Member;

public record MemberResponse(
        Long id,
        String email,
        String nickname
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getNickname());
    }
}
