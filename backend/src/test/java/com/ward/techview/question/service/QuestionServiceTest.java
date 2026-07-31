package com.ward.techview.question.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.question.dto.QuestionCandidate;
import com.ward.techview.question.dto.SelectedQuestion;
import com.ward.techview.question.entity.Question;
import com.ward.techview.question.entity.QuestionCategory;
import com.ward.techview.question.repository.QuestionRepository;
import com.ward.techview.question.response.QuestionResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock
    private QuestionRepository questionRepository;

    private QuestionService questionService;
    private Map<Long, Question> questionsById;

    @BeforeEach
    void setUp() {
        questionService = new QuestionService(
                questionRepository,
                new QuestionRandomizer(new Random(1234L))
        );
        questionsById = questionsById();
    }

    @Test
    @DisplayName("서로 다른 카테고리에서 핵심 질문 5개를 선정한다")
    void shouldSelectFiveCoreQuestionsFromDistinctCategories() {
        when(questionRepository.findAllCandidates()).thenReturn(candidates(questionsById));
        when(questionRepository.findAllById(any())).thenAnswer(invocation ->
                findQuestions(invocation.getArgument(0))
        );

        List<SelectedQuestion> result = questionService.selectCoreQuestions();

        assertAll(
                () -> assertEquals(5, result.size()),
                () -> assertEquals(5, result.stream()
                        .map(SelectedQuestion::category)
                        .distinct()
                        .count()),
                () -> assertTrue(result.stream()
                        .allMatch(question -> questionsById.containsKey(question.id())))
        );
        verify(questionRepository).findAllCandidates();
        verify(questionRepository).findAllById(any());
    }

    @Test
    @DisplayName("동일한 난수 seed를 사용하면 같은 질문을 같은 순서로 선정한다")
    void shouldProduceSameSelectionWithSameRandomSeed() {
        QuestionService anotherService = new QuestionService(
                questionRepository,
                new QuestionRandomizer(new Random(1234L))
        );
        when(questionRepository.findAllCandidates()).thenReturn(candidates(questionsById));
        when(questionRepository.findAllById(any())).thenAnswer(invocation ->
                findQuestions(invocation.getArgument(0))
        );

        List<SelectedQuestion> first = questionService.selectCoreQuestions();
        List<SelectedQuestion> second = anotherService.selectCoreQuestions();

        assertEquals(first, second);
    }

    @Test
    @DisplayName("질문이 있는 카테고리가 5개 미만이면 질문 풀 부족 예외가 발생한다")
    void shouldThrowWhenFewerThanFiveCategoriesHaveQuestions() {
        List<QuestionCandidate> candidates = List.of(
                new QuestionCandidate(1L, QuestionCategory.JAVA),
                new QuestionCandidate(2L, QuestionCategory.SPRING),
                new QuestionCandidate(3L, QuestionCategory.DATABASE),
                new QuestionCandidate(4L, QuestionCategory.NETWORK)
        );
        when(questionRepository.findAllCandidates()).thenReturn(candidates);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> questionService.selectCoreQuestions()
        );

        assertSame(QuestionResponseCode.QUESTION_POOL_INSUFFICIENT, exception.getResponseCode());
        verify(questionRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("선정한 질문을 모두 조회하지 못하면 질문 풀 부족 예외가 발생한다")
    void shouldThrowWhenSelectedQuestionsCannotAllBeLoaded() {
        when(questionRepository.findAllCandidates()).thenReturn(candidates(questionsById));
        when(questionRepository.findAllById(any())).thenReturn(
                new ArrayList<>(questionsById.values()).subList(0, 4)
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> questionService.selectCoreQuestions()
        );

        assertSame(QuestionResponseCode.QUESTION_POOL_INSUFFICIENT, exception.getResponseCode());
    }

    @Test
    @DisplayName("선정된 질문 목록은 수정할 수 없다")
    void shouldReturnUnmodifiableQuestionList() {
        when(questionRepository.findAllCandidates()).thenReturn(candidates(questionsById));
        when(questionRepository.findAllById(any())).thenAnswer(invocation ->
                findQuestions(invocation.getArgument(0))
        );

        List<SelectedQuestion> result = questionService.selectCoreQuestions();

        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    private List<Question> findQuestions(Iterable<Long> ids) {
        return StreamSupport.stream(ids.spliterator(), false)
                .map(questionsById::get)
                .toList();
    }

    private static List<QuestionCandidate> candidates(Map<Long, Question> questionsById) {
        return questionsById.entrySet().stream()
                .map(entry -> new QuestionCandidate(entry.getKey(), entry.getValue().getCategory()))
                .toList();
    }

    private static Map<Long, Question> questionsById() {
        Map<Long, Question> questions = new java.util.LinkedHashMap<>();
        long id = 1L;

        for (QuestionCategory category : QuestionCategory.values()) {
            questions.put(id, question(id, category, "질문 " + id));
            id++;
            questions.put(id, question(id, category, "질문 " + id));
            id++;
        }

        return questions;
    }

    private static Question question(Long id, QuestionCategory category, String content) {
        try {
            Constructor<Question> constructor = Question.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            Question question = constructor.newInstance();
            ReflectionTestUtils.setField(question, "id", id);
            ReflectionTestUtils.setField(question, "category", category);
            ReflectionTestUtils.setField(question, "content", content);
            return question;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Question 테스트 fixture 생성에 실패했습니다.", e);
        }
    }
}
