package com.ward.techview.account.auth.dto;

import com.ward.techview.account.member.entity.Member;
import com.ward.techview.common.security.IssuedToken;

public record LoginResponse(
        String accessToken,
        Long memberId,
        String email,
        String nickname
) {
    public static LoginResponse of(IssuedToken accessToken, Member member) {
        return new LoginResponse(accessToken.value(), member.getId(), member.getEmail(), member.getNickname());
    }
}
