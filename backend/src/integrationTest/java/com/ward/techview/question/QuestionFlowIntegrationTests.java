package com.ward.techview.question;

import com.ward.techview.question.dto.QuestionCandidate;
import com.ward.techview.question.api.SelectedQuestion;
import com.ward.techview.question.entity.QuestionCategory;
import com.ward.techview.question.repository.QuestionRepository;
import com.ward.techview.question.service.QuestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QuestionFlowIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionService questionService;

    @Test
    @DisplayName("초기 질문은 6개 카테고리에 각각 10개씩 저장된다")
    void shouldSeedTenQuestionsForEachCategory() {
        List<QuestionCandidate> candidates = questionRepository.findAllCandidates();
        Map<QuestionCategory, Long> countsByCategory = new EnumMap<>(QuestionCategory.class);

        candidates.forEach(candidate -> countsByCategory.merge(candidate.category(), 1L, Long::sum));

        assertAll(
                () -> assertEquals(60, candidates.size()),
                () -> assertEquals(QuestionCategory.values().length, countsByCategory.size()),
                () -> countsByCategory.values()
                        .forEach(count -> assertEquals(10L, count))
        );
    }

    @Test
    @DisplayName("실제 PostgreSQL 질문 풀에서 서로 다른 카테고리의 질문 5개를 선정한다")
    void shouldSelectFiveQuestionsFromDistinctCategories() {
        List<SelectedQuestion> selectedQuestions = questionService.selectCoreQuestions();

        assertAll(
                () -> assertEquals(5, selectedQuestions.size()),
                () -> assertEquals(5, selectedQuestions.stream()
                        .map(SelectedQuestion::category)
                        .distinct()
                        .count()),
                () -> selectedQuestions.forEach(question -> assertFalse(question.content().isBlank()))
        );
    }
}
