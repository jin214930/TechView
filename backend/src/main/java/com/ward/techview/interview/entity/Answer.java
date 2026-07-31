package com.ward.techview.interview.entity;

import com.ward.techview.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Answer extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY)
    // DB FK ON DELETE CASCADE: 면접 턴 삭제 시 해당 답변도 함께 삭제된다.
    @JoinColumn(nullable = false, unique = true)
    private InterviewTurn interviewTurn;

    @Column(nullable = false, length = 1024)
    private String audioObjectKey;

    @Column(nullable = false, length = 100)
    private String audioContentType;

    @Column(nullable = false)
    private int audioDurationMs;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    private OffsetDateTime transcribedAt;

    private Answer(InterviewTurn interviewTurn, String audioObjectKey, String audioContentType, int audioDurationMs) {
        this.interviewTurn = Objects.requireNonNull(interviewTurn);
        this.audioObjectKey = Objects.requireNonNull(audioObjectKey);
        this.audioContentType = Objects.requireNonNull(audioContentType);

        if (audioDurationMs <= 0) {
            throw new IllegalArgumentException("음성 재생 시간은 0보다 커야 합니다.");
        }

        this.audioDurationMs = audioDurationMs;
    }

    public static Answer create(InterviewTurn interviewTurn, String audioObjectKey, String audioContentType, int audioDurationMs) {
        return new Answer(interviewTurn, audioObjectKey, audioContentType, audioDurationMs);
    }
}
