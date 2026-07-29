package com.ward.techview.account.auth.controller;

import com.ward.techview.account.auth.response.AuthResponseCode;
import com.ward.techview.account.auth.dto.SignUpRequest;
import com.ward.techview.account.auth.service.AuthService;
import com.ward.techview.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<Void> signUp(@Valid @RequestBody SignUpRequest request) {
        authService.signUp(request);
        return ApiResponse.from(AuthResponseCode.SIGNUP_SUCCESS);
    }
}
