package com.ward.techview.account.auth.entity;

import com.ward.techview.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(indexes = {
        @Index(name = "idx_refresh_token_member_id", columnList = "member_id"),
        @Index(name = "idx_refresh_token_expires_at", columnList = "expires_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {
    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    private OffsetDateTime revokedAt;

    private RefreshToken(String token, Long memberId, OffsetDateTime expiresAt) {
        this.token = token;
        this.memberId = memberId;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken create(String token, Long memberId, OffsetDateTime expiresAt) {
        return new RefreshToken(token, memberId, expiresAt);
    }

    public void rotate(String token, OffsetDateTime expiresAt) {
        this.token = token;
        this.expiresAt = expiresAt;
        this.revokedAt = null;
    }

    public void revoke(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }
}
