-- 기존 행에는 JWT 원문이 저장되어 있어 HMAC 해시 방식과 호환되지 않는다.
-- MVP 개발 단계의 기존 로그인 세션을 무효화하고 이후 발급분부터 해시만 저장한다.
DELETE FROM refresh_token;

ALTER TABLE refresh_token
    ALTER COLUMN token TYPE VARCHAR(255);
