package com.ward.techview.interview.repository;

import com.ward.techview.interview.entity.InterviewDailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface InterviewDailyUsageRepository extends JpaRepository<InterviewDailyUsage, Long> {
    boolean existsByMemberIdAndUsageDate(Long memberId, LocalDate usageDate);
}
