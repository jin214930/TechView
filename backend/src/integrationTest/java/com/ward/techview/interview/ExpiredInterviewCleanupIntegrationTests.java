package com.ward.techview.interview;

import com.ward.techview.account.member.entity.Member;
import com.ward.techview.account.member.repository.MemberRepository;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.service.ExpiredInterviewCleanupBatchResult;
import com.ward.techview.interview.service.ExpiredInterviewCleanupBatchService;
import com.ward.techview.processing.entity.FinalFeedbackProcessingJob;
import com.ward.techview.processing.entity.ProcessingJob;
import com.ward.techview.processing.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExpiredInterviewCleanupIntegrationTests {
    private static final OffsetDateTime NOW = OffsetDateTime.of(
            2026, 8, 5, 0, 0, 0, 0, ZoneOffset.UTC
    );
    private static final OffsetDateTime CUTOFF = NOW.minusDays(30);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private ProcessingJobRepository processingJobRepository;

    @Autowired
    private ExpiredInterviewCleanupBatchService cleanupBatchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;

    @BeforeEach
    void setUp() {
        processingJobRepository.deleteAllInBatch();
        interviewSessionRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member member = memberRepository.saveAndFlush(
                Member.create(
                        "expired-cleanup@example.com",
                        "encoded-password",
                        "cleanup-user"
                )
        );
        memberId = member.getId();
    }

    @Test
    @DisplayName("만료 세션과 Processing Job을 FK Cascade로 함께 삭제한다")
    void deletesExpiredSessionAndProcessingJobByCascade() {
        InterviewSession session = createExpiredSession();
        ProcessingJob job = processingJobRepository.saveAndFlush(
                FinalFeedbackProcessingJob.create(
                        session.getId(),
                        NOW.minusDays(1)
                )
        );

        ExpiredInterviewCleanupBatchResult result = cleanupBatchService.cleanupBatch(
                NOW,
                CUTOFF,
                0L,
                100
        );

        assertAll(
                () -> assertEquals(1, result.scannedCount()),
                () -> assertEquals(1, result.deletedCount()),
                () -> assertFalse(interviewSessionRepository.existsById(session.getId())),
                () -> assertFalse(processingJobRepository.existsById(job.getId()))
        );
    }

    @Test
    @DisplayName("처리 lease가 유효한 작업이 있는 만료 세션은 삭제하지 않는다")
    void keepsExpiredSessionWithActivelyProcessingJob() {
        InterviewSession session = createExpiredSession();
        FinalFeedbackProcessingJob job = FinalFeedbackProcessingJob.create(
                session.getId(),
                NOW.minusDays(1)
        );
        job.claim(NOW.minusSeconds(30), Duration.ofMinutes(1));
        processingJobRepository.saveAndFlush(job);

        ExpiredInterviewCleanupBatchResult result = cleanupBatchService.cleanupBatch(
                NOW,
                CUTOFF,
                0L,
                100
        );

        assertAll(
                () -> assertEquals(1, result.scannedCount()),
                () -> assertEquals(0, result.deletedCount()),
                () -> assertTrue(interviewSessionRepository.existsById(session.getId())),
                () -> assertTrue(processingJobRepository.existsById(job.getId()))
        );
    }

    @Test
    @DisplayName("Flyway가 만료 세션 정리용 부분 인덱스를 생성한다")
    void createsExpiredInterviewCleanupIndex() {
        Integer indexCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname = 'idx_interview_session_expired_cleanup'
                        """,
                Integer.class
        );

        assertEquals(1, indexCount);
    }

    @Test
    @DisplayName("Flyway가 음성 객체 참조 조회 인덱스를 생성한다")
    void createsAnswerAudioObjectKeyIndex() {
        Integer indexCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname = 'idx_answer_audio_object_key'
                        """,
                Integer.class
        );

        assertEquals(1, indexCount);
    }

    private InterviewSession createExpiredSession() {
        return interviewSessionRepository.saveAndFlush(
                InterviewSession.create(
                        memberId,
                        CUTOFF.minusSeconds(1)
                )
        );
    }
}
