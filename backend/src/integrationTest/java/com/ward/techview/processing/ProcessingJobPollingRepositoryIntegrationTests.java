package com.ward.techview.processing;

import com.ward.techview.account.member.entity.Member;
import com.ward.techview.account.member.repository.MemberRepository;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.processing.entity.FinalFeedbackProcessingJob;
import com.ward.techview.processing.entity.ProcessingJob;
import com.ward.techview.processing.entity.ProcessingJobStatus;
import com.ward.techview.processing.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProcessingJobPollingRepositoryIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private ProcessingJobRepository processingJobRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long interviewSessionId;

    @BeforeEach
    void setUp() {
        processingJobRepository.deleteAllInBatch();
        interviewSessionRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member member = memberRepository.saveAndFlush(
                Member.create(
                        "processing-polling@example.com",
                        "encoded-password",
                        "polling-user"
                )
        );
        InterviewSession session = interviewSessionRepository.saveAndFlush(
                InterviewSession.create(
                        member.getId(),
                        OffsetDateTime.now(ZoneOffset.UTC)
                )
        );
        interviewSessionId = session.getId();
    }

    @Test
    @DisplayName("실행 시각이 된 대기 작업과 lease가 만료된 작업만 조회한다")
    void findsOnlyDispatchableJobs() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ProcessingJob queuedJob = processingJobRepository.save(
                FinalFeedbackProcessingJob.create(
                        interviewSessionId,
                        now.minusMinutes(1)
                )
        );
        ProcessingJob futureJob = processingJobRepository.save(
                FinalFeedbackProcessingJob.create(
                        interviewSessionId,
                        now.plusMinutes(1)
                )
        );
        FinalFeedbackProcessingJob expiredLeaseJob =
                FinalFeedbackProcessingJob.create(
                        interviewSessionId,
                        now.minusMinutes(2)
                );
        expiredLeaseJob.claim(
                now.minusMinutes(1),
                Duration.ofSeconds(1)
        );
        processingJobRepository.saveAndFlush(expiredLeaseJob);

        List<ProcessingJob> result =
                processingJobRepository.findDispatchableJobs(
                        List.of(
                                ProcessingJobStatus.QUEUED,
                                ProcessingJobStatus.RETRY_WAIT
                        ),
                        ProcessingJobStatus.PROCESSING,
                        now,
                        PageRequest.of(0, 20)
                );

        List<Long> resultIds = result.stream()
                .map(ProcessingJob::getId)
                .toList();
        assertAll(
                () -> assertTrue(resultIds.contains(queuedJob.getId())),
                () -> assertTrue(resultIds.contains(expiredLeaseJob.getId())),
                () -> assertFalse(resultIds.contains(futureJob.getId()))
        );
    }

    @Test
    @Transactional
    @DisplayName("실제 PostgreSQL에서 최대 시도 lease 만료 작업을 비관적 락으로 조회한다")
    void locksExhaustedExpiredJobs() {
        OffsetDateTime firstAttemptAt =
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
        FinalFeedbackProcessingJob exhaustedJob =
                FinalFeedbackProcessingJob.create(
                        interviewSessionId,
                        firstAttemptAt
                );
        exhaustedJob.claim(firstAttemptAt, Duration.ofSeconds(1));
        exhaustedJob.retry(
                firstAttemptAt.plusMinutes(1),
                "TEMPORARY_FAILURE",
                "일시적 오류"
        );
        exhaustedJob.claim(
                firstAttemptAt.plusMinutes(1),
                Duration.ofSeconds(1)
        );
        exhaustedJob.retry(
                firstAttemptAt.plusMinutes(2),
                "TEMPORARY_FAILURE",
                "일시적 오류"
        );
        exhaustedJob.claim(
                firstAttemptAt.plusMinutes(2),
                Duration.ofSeconds(1)
        );
        processingJobRepository.saveAndFlush(exhaustedJob);

        List<ProcessingJob> result =
                processingJobRepository.findExhaustedExpiredJobs(
                        ProcessingJobStatus.PROCESSING,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        PageRequest.of(0, 20)
                );

        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals(exhaustedJob.getId(), result.get(0).getId())
        );
    }

    @Test
    @DisplayName("Flyway가 processing job lease polling 인덱스를 생성한다")
    void createsLeasePollingIndex() {
        Integer indexCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname =
                              'idx_processing_job_status_lease_expires_at'
                        """,
                Integer.class
        );

        assertEquals(1, indexCount);
    }
}
