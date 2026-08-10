package com.ward.techview.interview.entity;

import com.ward.techview.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(
        name = "interview_daily_usage",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_interview_daily_usage_member_date",
                columnNames = {"member_id", "usage_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewDailyUsage extends BaseEntity {
    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private LocalDate usageDate;

    private InterviewDailyUsage(Long memberId, LocalDate usageDate) {
        this.memberId = Objects.requireNonNull(memberId, "회원 ID는 필수입니다.");
        this.usageDate = Objects.requireNonNull(usageDate, "사용 날짜는 필수입니다.");
    }

    public static InterviewDailyUsage create(Long memberId, LocalDate usageDate) {
        return new InterviewDailyUsage(memberId, usageDate);
    }
}
