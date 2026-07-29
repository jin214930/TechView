package com.ward.techview.account.member.controller;

import com.ward.techview.account.auth.cookie.RefreshTokenCookieManager;
import com.ward.techview.account.member.dto.MemberResponse;
import com.ward.techview.account.member.service.MemberService;
import com.ward.techview.common.response.ApiResponse;
import com.ward.techview.common.response.CommonResponseCode;
import com.ward.techview.common.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;
    private final RefreshTokenCookieManager refreshTokenCookieManager;

    @GetMapping("/me")
    public ApiResponse<MemberResponse> getMe(@AuthenticationPrincipal CustomUserDetails principal) {
        MemberResponse response = memberService.getMember(principal.id());
        return ApiResponse.from(CommonResponseCode.SUCCESS, response);
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> deleteMe(@AuthenticationPrincipal CustomUserDetails principal, HttpServletResponse response) {
        memberService.deleteMember(principal.id());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.expire().toString());
        return ApiResponse.from(CommonResponseCode.SUCCESS);
    }
}
