package com.ward.techview.account.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignUpRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("올바른 회원가입 요청은 검증에 성공한다")
    void shouldAcceptValidRequest() {
        SignUpRequest request = new SignUpRequest("user@example.com", "password123!", "사용자_1");

        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("이메일이 비어 있으면 검증에 실패한다")
    void shouldRejectBlankEmail(String email) {
        assertFieldViolation(new SignUpRequest(email, "password123!", "사용자"), "email");
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 검증에 실패한다")
    void shouldRejectInvalidEmailFormat() {
        assertFieldViolation(new SignUpRequest("invalid-email", "password123!", "사용자"), "email");
    }

    @Test
    @DisplayName("이메일은 정확히 100자까지 허용한다")
    void shouldAcceptEmailAtMaximumLength() {
        SignUpRequest request = new SignUpRequest(emailWithLength(100), "password123!", "사용자");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("이메일이 101자이면 검증에 실패한다")
    void shouldRejectEmailOverMaximumLength() {
        assertFieldViolation(
                new SignUpRequest(emailWithLength(101), "password123!", "사용자"),
                "email"
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("비밀번호가 비어 있으면 검증에 실패한다")
    void shouldRejectBlankPassword(String password) {
        assertFieldViolation(new SignUpRequest("user@example.com", password, "사용자"), "password");
    }

    @Test
    @DisplayName("비밀번호는 정확히 8자를 허용한다")
    void shouldAcceptPasswordAtMinimumLength() {
        SignUpRequest request = new SignUpRequest("user@example.com", "Abc123!@", "사용자");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("비밀번호는 정확히 64자를 허용한다")
    void shouldAcceptPasswordAtMaximumLength() {
        SignUpRequest request = new SignUpRequest(
                "user@example.com",
                "A".repeat(63) + "1",
                "사용자"
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 검증에 실패한다")
    void shouldRejectPasswordUnderMinimumLength() {
        assertFieldViolation(new SignUpRequest("user@example.com", "Abc123!", "사용자"), "password");
    }

    @Test
    @DisplayName("비밀번호가 64자를 초과하면 검증에 실패한다")
    void shouldRejectPasswordOverMaximumLength() {
        assertFieldViolation(
                new SignUpRequest("user@example.com", "A".repeat(64) + "1", "사용자"),
                "password"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"password 123!", "비밀번호123456"})
    @DisplayName("비밀번호에 공백이나 한글이 포함되면 검증에 실패한다")
    void shouldRejectPasswordContainingUnsupportedCharacters(String password) {
        assertFieldViolation(new SignUpRequest("user@example.com", password, "사용자"), "password");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("닉네임이 비어 있으면 검증에 실패한다")
    void shouldRejectBlankNickname(String nickname) {
        assertFieldViolation(new SignUpRequest("user@example.com", "password123!", nickname), "nickname");
    }

    @Test
    @DisplayName("닉네임은 정확히 2자를 허용한다")
    void shouldAcceptNicknameAtMinimumLength() {
        SignUpRequest request = new SignUpRequest("user@example.com", "password123!", "가나");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("닉네임은 정확히 20자를 허용한다")
    void shouldAcceptNicknameAtMaximumLength() {
        SignUpRequest request = new SignUpRequest(
                "user@example.com",
                "password123!",
                "가".repeat(20)
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("닉네임이 2자 미만이면 검증에 실패한다")
    void shouldRejectNicknameUnderMinimumLength() {
        assertFieldViolation(new SignUpRequest("user@example.com", "password123!", "가"), "nickname");
    }

    @Test
    @DisplayName("닉네임이 20자를 초과하면 검증에 실패한다")
    void shouldRejectNicknameOverMaximumLength() {
        assertFieldViolation(
                new SignUpRequest("user@example.com", "password123!", "가".repeat(21)),
                "nickname"
        );
    }

    @Test
    @DisplayName("닉네임에 허용되지 않은 문자가 포함되면 검증에 실패한다")
    void shouldRejectNicknameContainingUnsupportedCharacters() {
        assertFieldViolation(new SignUpRequest("user@example.com", "password123!", "사용자!"), "nickname");
    }

    private static void assertFieldViolation(SignUpRequest request, String field) {
        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals(field)));
    }

    private static String emailWithLength(int length) {
        String localPart = "a".repeat(64);
        int domainLabelLength = length - localPart.length() - "@.com".length();
        return localPart + "@" + "b".repeat(domainLabelLength) + ".com";
    }
}
